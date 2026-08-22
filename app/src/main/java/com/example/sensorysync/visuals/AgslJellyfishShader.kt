package com.example.sensorysync.visuals

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope

class AgslJellyfishShader {

    companion object {
        const val AGSL_SRC = """
            uniform float2 u_resolution;
            uniform float u_time;
            uniform float2 u_jelly_pos;
            uniform float u_heading;
            uniform float u_pulse;
            uniform float u_base_hue;
            uniform float u_is_focused;
            uniform float u_scale;

            vec3 hsv2rgb(vec3 c) {
                vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
                vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
                return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
            }

            vec2 rotate2D(vec2 p, float a) {
                float s = sin(a);
                float c = cos(a);
                return vec2(p.x * c - p.y * s, p.x * s + p.y * c);
            }

            half4 main(float2 fragCoord) {
                vec2 p = (fragCoord - u_jelly_pos);
                p = rotate2D(p, -(u_heading + 1.5707963));
                
                float scale = max(u_scale, 10.0);
                vec2 uv = p / scale;
                
                float rx = 1.0 + (1.0 - u_pulse) * 0.28;
                float ry = 0.82 * (1.0 + u_pulse * 0.28);
                
                vec3 finalColor = vec3(0.0);
                float finalAlpha = 0.0;
                
                // 1. Ambient Volumetric Glow Halo
                float distToCenter = length(uv);
                float glowHalo = exp(-distToCenter * 1.5) * (0.42 + u_is_focused * 0.40);
                vec3 glowColor = hsv2rgb(vec3(u_base_hue / 360.0, 0.82, 1.0));
                finalColor += glowColor * glowHalo;
                finalAlpha = max(finalAlpha, glowHalo * 0.65);
                
                // 2. 3D Ellipsoid Dome with Curved Subumbrella Rim
                vec2 domeUV = uv - vec2(0.0, -ry * 0.10);
                float domeDist = length(vec2(domeUV.x / rx, domeUV.y / ry));
                
                // 3D curved bottom margin with scalloped lappet frills
                float rimCurvature = (domeUV.x / rx) * (domeUV.x / rx) * (ry * 0.22);
                float scallopWave = sin(atan(domeUV.x, -domeUV.y) * 16.0 + u_time * 3.0) * 0.035;
                float bottomLimit = (ry * 0.32) - rimCurvature + scallopWave;
                
                if (domeDist < 1.04 && uv.y < bottomLimit) {
                    float nx = domeUV.x / rx;
                    float ny = domeUV.y / ry;
                    float nzSq = 1.0 - (nx * nx + ny * ny);
                    float nz = sqrt(max(nzSq, 0.0));
                    vec3 normal = normalize(vec3(nx, ny, nz));
                    
                    // Fresnel edge glow (luminous glass rim)
                    float fresnel = pow(1.0 - normal.z, 2.2);
                    
                    // Specular Apex Crown Highlight
                    vec3 lightDir = normalize(vec3(0.0, -0.45, 0.88));
                    float spec = pow(max(dot(reflect(-lightDir, normal), vec3(0.0, 0.0, 1.0)), 0.0), 16.0);
                    
                    // 3. Subsurface Scattering from Inner Organ Cluster (Vibrant Magenta/Rose Gonads)
                    float organRadiance = 0.0;
                    vec2 organCenter = vec2(0.0, -ry * 0.20);
                    for (int i = 0; i < 4; i++) {
                        float ang = float(i) * 1.5707963 + u_time * 0.4;
                        vec2 nodePos = organCenter + vec2(cos(ang) * 0.25, sin(ang) * 0.18);
                        float dNode = length(uv - nodePos);
                        organRadiance += exp(-dNode * 8.5);
                    }
                    
                    // Central Manubrium Core
                    float dCore = length(uv - organCenter);
                    float coreRadiance = exp(-dCore * 14.0);
                    
                    // 4. 16 Radial Striations / Neural Meridians
                    float angleAroundCap = atan(domeUV.x, -domeUV.y);
                    float ribs = pow(cos(angleAroundCap * 8.0), 10.0) * (1.0 - domeDist * 0.4);
                    
                    // Compose Glass Shading (Cyan Outer Glass + Rose/Magenta Subsurface + Golden Focus)
                    vec3 cyanGlass = hsv2rgb(vec3(u_base_hue / 360.0, 0.60, 1.0));
                    vec3 magentaOrgans = vec3(1.0, 0.18, 0.65); // Brilliant vibrant rose magenta
                    vec3 whiteCore = vec3(1.0, 1.0, 1.0);
                    vec3 goldAura = vec3(1.0, 0.85, 0.3);
                    
                    // Layer Glass Colors with Sub-surface Translucency
                    vec3 capColor = mix(cyanGlass, vec3(0.72, 0.28, 0.92), (1.0 - normal.z) * 0.6);
                    capColor += magentaOrgans * (organRadiance * (1.45 + u_is_focused * 0.75));
                    capColor += whiteCore * (coreRadiance * 1.6);
                    capColor += goldAura * (u_is_focused * 0.4);
                    capColor += vec3(1.0) * (spec * 0.95 + ribs * 0.45);
                    capColor += cyanGlass * (fresnel * 1.5);
                    
                    // Soft edge anti-aliasing on dome boundary
                    float edgeDist = max(1.04 - domeDist, 0.0) / 0.08;
                    float bottomDist = max(bottomLimit - uv.y, 0.0) / 0.06;
                    float edgeAA = clamp(min(edgeDist, bottomDist), 0.0, 1.0);
                    
                    float capAlpha = clamp((0.42 + fresnel * 0.55 + organRadiance * 0.40 + spec * 0.60) * edgeAA, 0.0, 1.0);
                    
                    finalColor = mix(finalColor, capColor, capAlpha);
                    finalAlpha = max(finalAlpha, capAlpha);
                }
                
                return half4(finalColor, clamp(finalAlpha, 0.0, 1.0));
            }
        """
    }

    private var runtimeShader: RuntimeShader? = null
    private var shaderBrush: ShaderBrush? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val shader = RuntimeShader(AGSL_SRC)
                runtimeShader = shader
                shaderBrush = ShaderBrush(shader)
            } catch (_: Exception) {}
        }
    }

    val isSupported: Boolean
        get() = runtimeShader != null && shaderBrush != null

    fun render(
        drawScope: DrawScope,
        width: Float,
        height: Float,
        animTime: Float,
        jx: Float,
        jy: Float,
        heading: Float,
        pulseVal: Float,
        baseHue: Float,
        isFocused: Boolean,
        scale: Float
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shader = runtimeShader ?: return
            val brush = shaderBrush ?: return

            shader.setFloatUniform("u_resolution", width, height)
            shader.setFloatUniform("u_time", animTime)
            shader.setFloatUniform("u_jelly_pos", jx, jy)
            shader.setFloatUniform("u_heading", heading)
            shader.setFloatUniform("u_pulse", pulseVal)
            shader.setFloatUniform("u_base_hue", baseHue)
            shader.setFloatUniform("u_is_focused", if (isFocused) 1.0f else 0.0f)
            shader.setFloatUniform("u_scale", scale)

            val boxRadius = scale * 2.4f
            val topLeft = Offset(jx - boxRadius, jy - boxRadius)
            val boxSize = Size(boxRadius * 2f, boxRadius * 2f)

            drawScope.drawRect(
                brush = brush,
                topLeft = topLeft,
                size = boxSize
            )
        }
    }
}
