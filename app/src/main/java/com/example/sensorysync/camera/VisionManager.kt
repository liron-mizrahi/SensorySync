package com.example.sensorysync.camera

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.Base64
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.sensorysync.model.EyeGazeData
import com.example.sensorysync.model.HandData
import com.example.sensorysync.model.HandGesture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot

data class FaceFingerprint(
    val eyeToEyeNormDist: Float = 0f,
    val eyeToNoseRatio: Float = 0f,
    val eyeToMouthRatio: Float = 0f,
    val noseToMouthRatio: Float = 0f,
    val mouthWidthRatio: Float = 0f,
    val boundingBoxAspectRatio: Float = 1f
)

class VisionManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onVisionUpdate: (HandData, HandData, EyeGazeData, Int?, String?, Boolean) -> Unit
) {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val prefs = context.getSharedPreferences("sensory_sync_face_profile", Context.MODE_PRIVATE)

    private val faceDetector: FaceDetector
    private val poseDetector: PoseDetector

    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null

    @Volatile
    private var isCameraStarted = false

    @Volatile
    private var lockedFaceId: Int? = null

    @Volatile
    private var savedFingerprint: FaceFingerprint? = null

    @Volatile
    private var latestFaceInFrame: Face? = null

    @Volatile
    private var latestSnapshotBase64: String? = null

    @Volatile
    private var lastSnapshotTimeMs: Long = 0L

    // Paints for MediaPipe visual landmark overlay
    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val landmarkPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val gazePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    init {
        val faceOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()
        faceDetector = FaceDetection.getClient(faceOptions)

        val poseOptions = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(poseOptions)

        loadSavedFaceProfile()
    }

    private fun loadSavedFaceProfile() {
        if (prefs.getBoolean("has_saved_profile", false)) {
            savedFingerprint = FaceFingerprint(
                eyeToEyeNormDist = prefs.getFloat("eye_to_eye", 0f),
                eyeToNoseRatio = prefs.getFloat("eye_to_nose", 0f),
                eyeToMouthRatio = prefs.getFloat("eye_to_mouth", 0f),
                noseToMouthRatio = prefs.getFloat("nose_to_mouth", 0f),
                mouthWidthRatio = prefs.getFloat("mouth_width", 0f),
                boundingBoxAspectRatio = prefs.getFloat("aspect_ratio", 1f)
            )
        }
    }

    fun setLockedFaceId(faceId: Int?) {
        this.lockedFaceId = faceId
        if (faceId != null && latestFaceInFrame != null && latestFaceInFrame?.trackingId == faceId) {
            saveAcquiredFace(latestFaceInFrame)
        }
    }

    fun saveAcquiredFace(face: Face?) {
        if (face == null) return
        val fp = extractFingerprint(face)
        savedFingerprint = fp
        val editor = prefs.edit()
            .putBoolean("has_saved_profile", true)
            .putFloat("eye_to_eye", fp.eyeToEyeNormDist)
            .putFloat("eye_to_nose", fp.eyeToNoseRatio)
            .putFloat("eye_to_mouth", fp.eyeToMouthRatio)
            .putFloat("nose_to_mouth", fp.noseToMouthRatio)
            .putFloat("mouth_width", fp.mouthWidthRatio)
            .putFloat("aspect_ratio", fp.boundingBoxAspectRatio)

        if (latestSnapshotBase64 != null) {
            editor.putString("saved_face_image_base64", latestSnapshotBase64)
        }
        editor.apply()
    }

    fun getSavedFaceImageBase64(): String? {
        return prefs.getString("saved_face_image_base64", null)
    }

    fun hasSavedFaceProfile(): Boolean = savedFingerprint != null

    private fun extractFingerprint(face: Face): FaceFingerprint {
        val bounds = face.boundingBox
        val aspect = if (bounds.height() > 0) bounds.width().toFloat() / bounds.height() else 1f

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position

        if (leftEye == null || rightEye == null) {
            return FaceFingerprint(boundingBoxAspectRatio = aspect)
        }

        val eyeDist = distance(leftEye, rightEye).coerceAtLeast(1.0f)
        val midEye = PointF((leftEye.x + rightEye.x) / 2f, (leftEye.y + rightEye.y) / 2f)

        val eyeToNose = if (nose != null) distance(midEye, nose) / eyeDist else 0.5f
        val mouthPos = mouthBottom ?: PointF((mouthLeft?.x ?: midEye.x), (mouthLeft?.y ?: midEye.y))
        val eyeToMouth = distance(midEye, mouthPos) / eyeDist
        val noseToMouth = if (nose != null) distance(nose, mouthPos) / eyeDist else 0.5f
        val mouthWidth = if (mouthLeft != null && mouthRight != null) distance(mouthLeft, mouthRight) / eyeDist else 0.5f

        return FaceFingerprint(
            eyeToEyeNormDist = eyeDist,
            eyeToNoseRatio = eyeToNose,
            eyeToMouthRatio = eyeToMouth,
            noseToMouthRatio = noseToMouth,
            mouthWidthRatio = mouthWidth,
            boundingBoxAspectRatio = aspect
        )
    }

    private fun computeDistance(fp1: FaceFingerprint, fp2: FaceFingerprint): Float {
        val dAspect = abs(fp1.boundingBoxAspectRatio - fp2.boundingBoxAspectRatio) * 0.4f
        val dEn = abs(fp1.eyeToNoseRatio - fp2.eyeToNoseRatio) * 0.3f
        val dEm = abs(fp1.eyeToMouthRatio - fp2.eyeToMouthRatio) * 0.3f
        val dMw = abs(fp1.mouthWidthRatio - fp2.mouthWidthRatio) * 0.2f
        return dAspect + dEn + dEm + dMw
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        return hypot(p1.x - p2.x, p1.y - p2.y)
    }

    fun startCamera(previewView: androidx.camera.view.PreviewView? = null) {
        if (isCameraStarted) {
            previewView?.let { pView ->
                previewUseCase?.setSurfaceProvider(pView.surfaceProvider)
            }
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    previewView?.let { pView ->
                        it.setSurfaceProvider(pView.surfaceProvider)
                    }
                }
                previewUseCase = preview

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy)
                        }
                    }
                imageAnalysisUseCase = imageAnalyzer

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                isCameraStarted = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val imageWidth = image.width.toFloat()
        val imageHeight = image.height.toFloat()

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                latestFaceInFrame = faces.firstOrNull()
                val (eyeData, detectedFaceId, isAutoMatched) = parseEyeGazeWithAutoMatch(faces, imageWidth, imageHeight)

                // Capture and draw MediaPipe visual landmark markers directly on the camera snapshot (2 FPS)
                var snapshotBase64: String? = null
                val now = System.currentTimeMillis()
                if (now - lastSnapshotTimeMs > 500) {
                    lastSnapshotTimeMs = now
                    try {
                        val rawBmp = imageProxy.toBitmap()
                        val annotatedBmp = Bitmap.createScaledBitmap(rawBmp, 160, 120, true).copy(Bitmap.Config.ARGB_8888, true)
                        val canvas = Canvas(annotatedBmp)

                        drawMediaPipeMarkersOnCanvas(
                            canvas = canvas,
                            faces = faces,
                            imgW = imageWidth,
                            imgH = imageHeight,
                            canvasW = 160f,
                            canvasH = 120f,
                            isLocked = lockedFaceId != null
                        )

                        val baos = ByteArrayOutputStream()
                        annotatedBmp.compress(Bitmap.CompressFormat.JPEG, 48, baos)
                        snapshotBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        latestSnapshotBase64 = snapshotBase64
                    } catch (_: Exception) {}
                }

                poseDetector.process(image)
                    .addOnSuccessListener { pose ->
                        val (leftHand, rightHand) = parseHands(pose, imageWidth, imageHeight)
                        onVisionUpdate(leftHand, rightHand, eyeData, detectedFaceId, snapshotBase64, isAutoMatched)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
            .addOnFailureListener {
                imageProxy.close()
            }
    }

    private fun drawMediaPipeMarkersOnCanvas(
        canvas: Canvas,
        faces: List<Face>,
        imgW: Float,
        imgH: Float,
        canvasW: Float,
        canvasH: Float,
        isLocked: Boolean
    ) {
        if (faces.isEmpty()) return

        val sx = canvasW / imgW
        val sy = canvasH / imgH

        for (face in faces) {
            val isTargetFace = if (lockedFaceId != null) face.trackingId == lockedFaceId else true
            val activeColor = if (isLocked && isTargetFace) AndroidColor.parseColor("#00E676") else AndroidColor.parseColor("#00E5FF")

            boxPaint.color = activeColor
            landmarkPaint.color = activeColor
            gazePaint.color = activeColor

            // 1. Draw Bounding Box with Corner Accents
            val b = face.boundingBox
            val rect = RectF(b.left * sx, b.top * sy, b.right * sx, b.bottom * sy)
            canvas.drawRoundRect(rect, 4f, 4f, boxPaint)

            // 2. Draw Landmark Points (Eyes, Nose, Mouth Corners)
            val landmarks = listOfNotNull(
                face.getLandmark(FaceLandmark.LEFT_EYE)?.position,
                face.getLandmark(FaceLandmark.RIGHT_EYE)?.position,
                face.getLandmark(FaceLandmark.NOSE_BASE)?.position,
                face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position,
                face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position,
                face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
            )

            for (pt in landmarks) {
                canvas.drawCircle(pt.x * sx, pt.y * sy, 2.5f, landmarkPaint)
            }

            // 3. Connect Eyes and Nose to form Landmark Triangle Mesh
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
            val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position

            if (leftEye != null && rightEye != null) {
                canvas.drawLine(leftEye.x * sx, leftEye.y * sy, rightEye.x * sx, rightEye.y * sy, gazePaint)
                if (nose != null) {
                    canvas.drawLine(leftEye.x * sx, leftEye.y * sy, nose.x * sx, nose.y * sy, gazePaint)
                    canvas.drawLine(rightEye.x * sx, rightEye.y * sy, nose.x * sx, nose.y * sy, gazePaint)
                }
            }
        }
    }

    private fun parseEyeGazeWithAutoMatch(faces: List<Face>, imgW: Float, imgH: Float): Triple<EyeGazeData, Int?, Boolean> {
        if (faces.isEmpty()) return Triple(EyeGazeData(isFaceDetected = false), null, false)

        var isAutoMatched = false
        var targetId = lockedFaceId

        if (targetId == null && savedFingerprint != null) {
            val targetFp = savedFingerprint!!
            var bestDist = Float.MAX_VALUE
            var bestFace: Face? = null

            for (f in faces) {
                val fp = extractFingerprint(f)
                val dist = computeDistance(fp, targetFp)
                if (dist < 0.28f && dist < bestDist) {
                    bestDist = dist
                    bestFace = f
                }
            }

            if (bestFace != null && bestFace.trackingId != null) {
                targetId = bestFace.trackingId
                lockedFaceId = targetId
                isAutoMatched = true
            }
        }

        val face = if (targetId != null) {
            faces.firstOrNull { it.trackingId == targetId }
        } else {
            faces.firstOrNull()
        }

        if (face == null) {
            return Triple(EyeGazeData(isFaceDetected = false), faces.firstOrNull()?.trackingId, false)
        }

        val bounds = face.boundingBox
        val normX = 1.0f - (bounds.centerX() / imgW).coerceIn(0f, 1f)
        val normY = (bounds.centerY() / imgH).coerceIn(0f, 1f)

        val leftEyeOpen = face.leftEyeOpenProbability ?: 1.0f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 1.0f
        val isBlinking = leftEyeOpen < 0.2f && rightEyeOpen < 0.2f

        val rawOffset = Offset(normX, normY)

        val eyeData = EyeGazeData(
            isFaceDetected = true,
            gazePosition = rawOffset,
            calibratedGazePosition = rawOffset,
            leftEyeOpenProb = leftEyeOpen,
            rightEyeOpenProb = rightEyeOpen,
            isBlinking = isBlinking,
            headRotationY = face.headEulerAngleY,
            headRotationZ = face.headEulerAngleZ,
            faceTrackingId = face.trackingId
        )

        return Triple(eyeData, face.trackingId, isAutoMatched)
    }

    private fun parseHands(pose: Pose, imgW: Float, imgH: Float): Pair<HandData, HandData> {
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val leftIndex = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)
        val leftThumb = pose.getPoseLandmark(PoseLandmark.LEFT_THUMB)

        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val rightIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)
        val rightThumb = pose.getPoseLandmark(PoseLandmark.RIGHT_THUMB)

        val leftHand = buildHandData(leftWrist, leftIndex, leftThumb, imgW, imgH)
        val rightHand = buildHandData(rightWrist, rightIndex, rightThumb, imgW, imgH)

        return Pair(leftHand, rightHand)
    }

    private fun buildHandData(
        wrist: PoseLandmark?,
        index: PoseLandmark?,
        thumb: PoseLandmark?,
        imgW: Float,
        imgH: Float
    ): HandData {
        if (wrist == null || wrist.inFrameLikelihood < 0.5f) {
            return HandData(isPresent = false)
        }

        val normX = 1.0f - (wrist.position.x / imgW).coerceIn(0f, 1f)
        val normY = (wrist.position.y / imgH).coerceIn(0f, 1f)

        var pinchDist = 1.0f
        var gesture = HandGesture.OPEN_PALM

        if (index != null && thumb != null && index.inFrameLikelihood > 0.4f && thumb.inFrameLikelihood > 0.4f) {
            val dx = (index.position.x - thumb.position.x) / imgW
            val dy = (index.position.y - thumb.position.y) / imgH
            pinchDist = (hypot(dx, dy) * 3f).coerceIn(0f, 1f)

            if (pinchDist < 0.25f) {
                gesture = HandGesture.PINCH
            }
        }

        return HandData(
            isPresent = true,
            position = Offset(normX, normY),
            pinchDistance = pinchDist,
            gesture = gesture,
            fingerCount = if (gesture == HandGesture.PINCH) 1 else 5
        )
    }

    fun stop() {
        try {
            isCameraStarted = false
            cameraProvider?.unbindAll()
            faceDetector.close()
            poseDetector.close()
            cameraExecutor.shutdown()
        } catch (_: Exception) {}
    }
}
