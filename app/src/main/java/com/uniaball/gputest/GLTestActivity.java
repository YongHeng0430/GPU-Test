package com.uniaball.gputest;

import android.app.Activity;
import android.opengl.GLES32;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLTestActivity extends Activity {
    private static final String TAG = "MassiveSphereDemo";
    private GLSurfaceView glSurfaceView;
    private TextView fpsTextView, infoTextView, countdownTextView;
    private int frameCount;
    private long lastTime;
    private long startTime;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    private boolean isTesting = false;
    private long testStartTime = 0;
    private int testFrameCount = 0;
    private int testDuration = 5000; // 5秒测试时间
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gltest);
        
        // 禁止屏幕休眠
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        FrameLayout container = findViewById(R.id.gl_container);
        fpsTextView = findViewById(R.id.fpsTextView);
        infoTextView = findViewById(R.id.infoTextView);
        countdownTextView = findViewById(R.id.countdownTextView);
        
        glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.setEGLContextClientVersion(3);
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);
        glSurfaceView.setRenderer(new SphereRenderer());
        container.addView(glSurfaceView);
        
        startTime = System.currentTimeMillis();
        startFPSCounter();
        
        infoTextView.setText("大规模球体渲染测试 - 100,000个球体");
        
        // 直接开始测试
        startPerformanceTest();
    }
    
    // 启动性能测试
    private void startPerformanceTest() {
        isTesting = true;
        testStartTime = System.currentTimeMillis();
        testFrameCount = 0;
        countdownTextView.setText("渲染测试中...5秒后结束");
        
        // 5秒后自动结束测试
        handler.postDelayed(() -> endPerformanceTest(), testDuration);
    }
    
    // 结束测试并显示结果
    private void endPerformanceTest() {
        isTesting = false;
        long testTime = System.currentTimeMillis() - testStartTime;
        float avgFPS = (testFrameCount * 1000f) / testTime;
        
        String performanceRating = getPerformanceRating(avgFPS);
        String result = String.format("测试结束!\n平均FPS: %.1f\n性能评级: %s", avgFPS, performanceRating);
        
        Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        countdownTextView.setText(result);
    }
    
    // GPU性能评级标准
    private String getPerformanceRating(float fps) {
        if (fps >= 60) {
            return "卓越 (Outstanding)";
        } else if (fps >= 45) {
            return "优秀 (Excellent)";
        } else if (fps >= 30) {
            return "良好 (Good)";
        } else if (fps >= 20) {
            return "中等 (Average)";
        } else if (fps >= 10) {
            return "一般 (Below Average)";
        } else {
            return "较差 (Poor)";
        }
    }
    
    private void startFPSCounter() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - lastTime;
                if (elapsed > 0) {
                    final float fps = (frameCount * 1000f) / elapsed;
                    runOnUiThread(() -> fpsTextView.setText(String.format("FPS: %.1f | Time: %ds", fps,
                            (System.currentTimeMillis() - startTime) / 1000)));
                }
                frameCount = 0;
                lastTime = currentTime;
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) {
            glSurfaceView.onPause();
            glSurfaceView.queueEvent(() -> {
                if (sphereRenderer != null) {
                    sphereRenderer.release();
                }
            });
        }
        handler.removeCallbacksAndMessages(null);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (glSurfaceView != null) {
            glSurfaceView.onResume();
        }
        lastTime = System.currentTimeMillis();
        frameCount = 0;
    }
    
    private SphereRenderer sphereRenderer;
    
    private class SphereRenderer implements GLSurfaceView.Renderer {
        private int shaderProgram;
        private int vertexBuffer;
        private int normalBuffer;
        private int indexBuffer;
        private int vertexCount;
        private int indexCount;
        private int instanceBuffer;
        private int vao; // 顶点数组对象(VAO)
        
        private static final int SPHERE_COUNT = 100000; // 十万个球体
        
        // 模型、视图、投影矩阵
        private final float[] viewMatrix = new float[16];
        private final float[] projectionMatrix = new float[16];
        private final float[] viewProjectionMatrix = new float[16];
        
        private long startTime;
        
        // 着色器统一变量位置缓存
        private int uTimeLoc;
        private int uLightPosLoc;
        private int uViewProjectionMatrixLoc;
        private int uCameraPosLoc; // 新增相机位置统一变量
        
        // 顶点着色器 - 修复法线变换问题
        private final String vertexShaderCode =
                "#version 320 es\n" +
                        "layout(location = 0) in vec3 aPosition;\n" +
                        "layout(location = 1) in vec3 aNormal;\n" +
                        "layout(location = 2) in vec3 aInstancePos;\n" +  // 实例位置
                        "layout(location = 3) in vec3 aInstanceParams;\n" + // 实例参数 (速度, 旋转速度, 偏移)
                        "\n" +
                        "uniform mat4 uViewProjectionMatrix;\n" +
                        "uniform float uTime;\n" +
                        "\n" +
                        "out vec3 vPosition;\n" +
                        "out vec3 vNormal;\n" +
                        "\n" +
                        "void main() {\n" +
                        "    // 从实例参数中提取值\n" +
                        "    float speed = aInstanceParams.x;\n" +
                        "    float rotationSpeed = aInstanceParams.y;\n" +
                        "    float offset = aInstanceParams.z;\n" +
                        "    \n" +
                        "    // 球体位置动画 \n" +
                        "    float timeOffset = uTime * speed + offset;\n" +
                        "    vec3 animatedPos = aInstancePos;\n" +
                        "    animatedPos.x += sin(timeOffset) * 5.0;\n" +
                        "    animatedPos.z += cos(timeOffset * 0.7) * 5.0;\n" +
                        "    animatedPos.y += sin(timeOffset * 1.3) * 2.0;\n" +
                        "    \n" +
                        "    // 球体旋转动画 \n" +
                        "    float angle = uTime * rotationSpeed + offset * 10.0;\n" +
                        "    float sinA = sin(angle);\n" +
                        "    float cosA = cos(angle);\n" +
                        "    \n" +
                        "    // 构建3x3旋转矩阵 (围绕Y轴)\n" +
                        "    mat3 rotationMatrix = mat3(\n" +
                        "        cosA, 0.0, -sinA,\n" +
                        "        0.0, 1.0, 0.0,\n" +
                        "        sinA, 0.0, cosA\n" +
                        "    );\n" +
                        "    \n" +
                        "    // 应用旋转矩阵到顶点和法线\n" +
                        "    vec3 rotatedPosition = rotationMatrix * aPosition;\n" +
                        "    vec3 rotatedNormal = rotationMatrix * aNormal;\n" +
                        "    \n" +
                        "    // 应用实例位置\n" +
                        "    vec3 worldPos = rotatedPosition + animatedPos;\n" +
                        "    \n" +
                        "    gl_Position = uViewProjectionMatrix * vec4(worldPos, 1.0);\n" +
                        "    vPosition = worldPos;\n" +
                        "    vNormal = rotatedNormal; // 传递旋转后的法线\n" +
                        "}\n";
        
        // 片段着色器 - 修复高光位置问题
        private final String fragmentShaderCode =
                "#version 320 es\n" +
                        "precision mediump float;\n" +
                        "in vec3 vPosition;\n" +
                        "in vec3 vNormal;\n" +
                        "out vec4 fragColor;\n" +
                        "\n" +
                        "uniform vec3 uLightPos;\n" +
                        "uniform vec3 uCameraPos; // 相机位置\n" +
                        "\n" +
                        "vec3 materialColor = vec3(0.8, 0.3, 0.2);\n" +
                        "\n" +
                        "void main() {\n" +
                        "    // 环境光\n" +
                        "    float ambient = 0.1;\n" +
                        "    \n" +
                        "    // 重新归一化法线\n" +
                        "    vec3 normal = normalize(vNormal);\n" +
                        "    \n" +
                        "    // 计算光源方向\n" +
                        "    vec3 lightDir = normalize(uLightPos - vPosition);\n" +
                        "    \n" +
                        "    // 计算视线方向\n" +
                        "    vec3 viewDir = normalize(uCameraPos - vPosition);\n" +
                        "    \n" +
                        "    // 修复的高光计算 (Phong模型)\n" +
                        "    vec3 reflectDir = reflect(-lightDir, normal);\n" +
                        "    float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);\n" +
                        "    \n" +
                        "    // 漫反射\n" +
                        "    float diff = max(dot(normal, lightDir), 0.0);\n" +
                        "    \n" +
                        "    // 组合光照 (修复高光在阴影中的问题)\n" +
                        "    vec3 lighting = materialColor * (ambient + diff) + vec3(0.3) * spec;\n" +
                        "    \n" +
                        "    // 根据位置添加颜色变化\n" +
                        "    float posFactor = vPosition.x * 0.1 + vPosition.y * 0.1 + vPosition.z * 0.1;\n" +
                        "    vec3 result = lighting;\n" +
                        "    result.r *= 0.8 + sin(posFactor) * 0.2;\n" +
                        "    result.g *= 0.8 + cos(posFactor) * 0.2;\n" +
                        "    result.b *= 0.8 + sin(posFactor * 1.2) * 0.2;\n" +
                        "    \n" +
                        "    fragColor = vec4(result, 1.0);\n" +
                        "}\n";
        
        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            // 检查OpenGL ES版本
            String version = GLES32.glGetString(GLES32.GL_VERSION);
            if (version == null || !version.startsWith("OpenGL ES 3.")) {
                runOnUiThread(() -> {
                    Toast.makeText(GLTestActivity.this, "需要 OpenGL ES 3.0+ 支持", Toast.LENGTH_LONG).show();
                    finish();
                });
                return;
            }
            
            // 创建顶点数组对象(VAO)
            int[] vaos = new int[1];
            GLES32.glGenVertexArrays(1, vaos, 0);
            vao = vaos[0];
            GLES32.glBindVertexArray(vao);
            
            // 创建着色器程序
            int vertexShader = loadShader(GLES32.GL_VERTEX_SHADER, vertexShaderCode);
            int fragmentShader = loadShader(GLES32.GL_FRAGMENT_SHADER, fragmentShaderCode);
            
            shaderProgram = GLES32.glCreateProgram();
            GLES32.glAttachShader(shaderProgram, vertexShader);
            GLES32.glAttachShader(shaderProgram, fragmentShader);
            GLES32.glLinkProgram(shaderProgram);
            
            // 删除着色器对象，释放资源
            GLES32.glDeleteShader(vertexShader);
            GLES32.glDeleteShader(fragmentShader);
            
            // 检查程序链接状态
            int[] linkStatus = new int[1];
            GLES32.glGetProgramiv(shaderProgram, GLES32.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                Log.e(TAG, "程序链接失败: " + GLES32.glGetProgramInfoLog(shaderProgram));
                shaderProgram = 0;
                return;
            }
            
            // 缓存统一变量位置
            uTimeLoc = GLES32.glGetUniformLocation(shaderProgram, "uTime");
            uLightPosLoc = GLES32.glGetUniformLocation(shaderProgram, "uLightPos");
            uViewProjectionMatrixLoc = GLES32.glGetUniformLocation(shaderProgram, "uViewProjectionMatrix");
            uCameraPosLoc = GLES32.glGetUniformLocation(shaderProgram, "uCameraPos"); // 新增相机位置
            
            // 创建球体模型
            createSphere(0.2f, 16); // 球体半径0.2，16段
            
            // 创建实例数据
            createInstances();
            
            // 设置顶点属性指针 (一次性设置)
            setupVertexAttributes();
            
            // 初始矩阵
            Matrix.setLookAtM(viewMatrix, 0,
                    0, 20, 50,   // 相机位置 (从上方远处观察)
                    0, 0, 0,     // 观察点
                    0, 1, 0);    // 上向量
            
            GLES32.glEnable(GLES32.GL_DEPTH_TEST);
            GLES32.glEnable(GLES32.GL_CULL_FACE);
            
            // 解绑VAO
            GLES32.glBindVertexArray(0);
            
            startTime = System.currentTimeMillis();
        }
        
        private void setupVertexAttributes() {
            // 设置顶点属性指针 (使用VAO)
            GLES32.glUseProgram(shaderProgram);
            
            // 位置属性
            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vertexBuffer);
            int positionLoc = GLES32.glGetAttribLocation(shaderProgram, "aPosition");
            GLES32.glEnableVertexAttribArray(positionLoc);
            GLES32.glVertexAttribPointer(positionLoc, 3, GLES32.GL_FLOAT, false, 0, 0);
            
            // 法线属性
            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, normalBuffer);
            int normalLoc = GLES32.glGetAttribLocation(shaderProgram, "aNormal");
            GLES32.glEnableVertexAttribArray(normalLoc);
            GLES32.glVertexAttribPointer(normalLoc, 3, GLES32.GL_FLOAT, false, 0, 0);
            
            // 实例位置属性
            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, instanceBuffer);
            int instancePosLoc = GLES32.glGetAttribLocation(shaderProgram, "aInstancePos");
            GLES32.glEnableVertexAttribArray(instancePosLoc);
            GLES32.glVertexAttribPointer(instancePosLoc, 3, GLES32.GL_FLOAT, false, 6 * 4, 0);
            GLES32.glVertexAttribDivisor(instancePosLoc, 1); // 每个实例更新一次
            
            // 实例参数属性
            int instanceParamsLoc = GLES32.glGetAttribLocation(shaderProgram, "aInstanceParams");
            GLES32.glEnableVertexAttribArray(instanceParamsLoc);
            GLES32.glVertexAttribPointer(instanceParamsLoc, 3, GLES32.GL_FLOAT, false, 6 * 4, 3 * 4);
            GLES32.glVertexAttribDivisor(instanceParamsLoc, 1); // 每个实例更新一次
            
            // 解绑缓冲区
            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0);
        }
        
        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            if (width == 0 || height == 0) return;
            
            // 设置视口和投影矩阵
            GLES32.glViewport(0, 0, width, height);
            float ratio = (float) width / height;
            Matrix.perspectiveM(projectionMatrix, 0, 45.0f, ratio, 0.1f, 1000.0f);
            
            // 预计算视图投影矩阵
            Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
        }
        
        @Override
        public void onDrawFrame(GL10 unused) {
            if (shaderProgram == 0) return;
            
            // 清除屏幕
            GLES32.glClearColor(0.05f, 0.05f, 0.1f, 1.0f);
            GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT | GLES32.GL_DEPTH_BUFFER_BIT);
            
            // 使用着色器程序
            GLES32.glUseProgram(shaderProgram);
            
            // 设置时间统一变量
            float time = (System.currentTimeMillis() - startTime) / 1000f;
            if (uTimeLoc != -1) GLES32.glUniform1f(uTimeLoc, time);
            
            // 设置光源位置 (在场景中移动)
            if (uLightPosLoc != -1) {
                float lightX = (float) Math.sin(time) * 50.0f;
                float lightY = 50.0f;
                float lightZ = (float) Math.cos(time) * 50.0f;
                GLES32.glUniform3f(uLightPosLoc, lightX, lightY, lightZ);
            }
            
            // 设置相机位置统一变量 (修复高光问题的关键)
            if (uCameraPosLoc != -1) {
                // 从视图矩阵的逆矩阵提取相机位置
                float[] invViewMatrix = new float[16];
                Matrix.invertM(invViewMatrix, 0, viewMatrix, 0);
                float cameraX = invViewMatrix[12];
                float cameraY = invViewMatrix[13];
                float cameraZ = invViewMatrix[14];
                GLES32.glUniform3f(uCameraPosLoc, cameraX, cameraY, cameraZ);
            }
            
            // 设置视图投影矩阵
            if (uViewProjectionMatrixLoc != -1) {
                GLES32.glUniformMatrix4fv(uViewProjectionMatrixLoc, 1, false, viewProjectionMatrix, 0);
            }
            
            // 绑定VAO
            GLES32.glBindVertexArray(vao);
            
            // 绑定索引缓冲区
            GLES32.glBindBuffer(GLES32.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            
            // 绘制所有球体实例
            GLES32.glDrawElementsInstanced(
                    GLES32.GL_TRIANGLES,
                    indexCount,
                    GLES32.GL_UNSIGNED_SHORT,
                    0,
                    SPHERE_COUNT
            );
            
            // 解绑VAO和索引缓冲区
            GLES32.glBindVertexArray(0);
            GLES32.glBindBuffer(GLES32.GL_ELEMENT_ARRAY_BUFFER, 0);
            
            // 更新计数器
            frameCount++;
            if (isTesting) testFrameCount++;
        }
        
        private void createSphere(float radius, int segments) {
            // 生成球体顶点数据
            int vertexCount = (segments + 1) * (segments + 1);
            FloatBuffer vertices = ByteBuffer.allocateDirect(vertexCount * 3 * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            
            FloatBuffer normals = ByteBuffer.allocateDirect(vertexCount * 3 * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            
            for (int i = 0; i <= segments; i++) {
                double lat = Math.PI * i / segments;
                for (int j = 0; j <= segments; j++) {
                    double lon = 2 * Math.PI * j / segments;
                    float x = (float) (Math.sin(lat) * Math.cos(lon));
                    float y = (float) Math.cos(lat);
                    float z = (float) (Math.sin(lat) * Math.sin(lon));
                    
                    vertices.put(x * radius);
                    vertices.put(y * radius);
                    vertices.put(z * radius);
                    
                    normals.put(x);
                    normals.put(y);
                    normals.put(z);
                }
            }
            vertices.position(0);
            normals.position(0);
            
            // 生成索引数据
            int indexCount = segments * segments * 6;
            ShortBuffer indices = ByteBuffer.allocateDirect(indexCount * 2)
                    .order(ByteOrder.nativeOrder()).asShortBuffer();
            
            for (int i = 0; i < segments; i++) {
                for (int j = 0; j < segments; j++) {
                    int start = i * (segments + 1) + j;
                    indices.put((short) start);
                    indices.put((short) (start + 1));
                    indices.put((short) (start + segments + 1));
                    indices.put((short) (start + segments + 1));
                    indices.put((short) (start + 1));
                    indices.put((short) (start + segments + 2));
                }
            }
            indices.position(0);
            
            // 创建顶点缓冲区
            int[] buffers = new int[3];
            GLES32.glGenBuffers(3, buffers, 0);
            
            // 顶点位置
            vertexBuffer = buffers[0];
            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vertexBuffer);
            GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, vertices.remaining() * 4, vertices, GLES32.GL_STATIC_DRAW);
            
            // 法线
            normalBuffer = buffers[1];
            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, normalBuffer);
            GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, normals.remaining() * 4, normals, GLES32.GL_STATIC_DRAW);
            
            // 索引
            indexBuffer = buffers[2];
            GLES32.glBindBuffer(GLES32.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            GLES32.glBufferData(GLES32.GL_ELEMENT_ARRAY_BUFFER, indices.remaining() * 2, indices, GLES32.GL_STATIC_DRAW);
            
            this.vertexCount = vertexCount;
            this.indexCount = indexCount;
            
            // 解绑缓冲区
            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0);
            GLES32.glBindBuffer(GLES32.GL_ELEMENT_ARRAY_BUFFER, 0);
        }
        
        private void createInstances() {
            // 生成实例数据 (位置和动画参数)
            float[] instanceData = new float[SPHERE_COUNT * 6]; // 每个实例6个浮点数 (位置xyz + 参数xyz)
            
            Random rand = new Random();
            for (int i = 0; i < SPHERE_COUNT; i++) {
                // 位置 (分布在100x100x100的空间内)
                float x = (rand.nextFloat() - 0.5f) * 200.0f;
                float y = (rand.nextFloat() - 0.5f) * 200.0f;
                float z = (rand.nextFloat() - 0.5f) * 200.0f;
                
                // 动画参数 (速度, 旋转速度, 随机偏移)
                float speed = 0.1f + rand.nextFloat() * 0.4f;
                float rotationSpeed = 0.5f + rand.nextFloat() * 1.0f;
                float offset = rand.nextFloat() * 10.0f;
                
                instanceData[i * 6] = x;
                instanceData[i * 6 + 1] = y;
                instanceData[i * 6 + 2] = z;
                instanceData[i * 6 + 3] = speed;
                instanceData[i * 6 + 4] = rotationSpeed;
                instanceData[i * 6 + 5] = offset;
            }
            
            // 创建实例缓冲区
            int[] buffers = new int[1];
            GLES32.glGenBuffers(1, buffers, 0);
            instanceBuffer = buffers[0];
            
            FloatBuffer instanceBufferData = ByteBuffer.allocateDirect(instanceData.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            instanceBufferData.put(instanceData).position(0);
            
            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, instanceBuffer);
            GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, instanceData.length * 4, instanceBufferData, GLES32.GL_STATIC_DRAW);
            
            // 解绑缓冲区
            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0);
        }
        
        private int loadShader(int type, String shaderCode) {
            int shader = GLES32.glCreateShader(type);
            GLES32.glShaderSource(shader, shaderCode);
            GLES32.glCompileShader(shader);
            
            // 检查编译状态
            int[] compiled = new int[1];
            GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String log = GLES32.glGetShaderInfoLog(shader);
                Log.e(TAG, "着色器编译失败: " + log);
                GLES32.glDeleteShader(shader);
                return 0;
            }
            return shader;
        }
        
        public void release() {
            if (shaderProgram != 0) {
                GLES32.glDeleteProgram(shaderProgram);
                shaderProgram = 0;
            }
            
            int[] buffersToDelete = new int[]{vertexBuffer, normalBuffer, indexBuffer, instanceBuffer};
            GLES32.glDeleteBuffers(buffersToDelete.length, buffersToDelete, 0);
            
            if (vao != 0) {
                int[] vaos = new int[]{vao};
                GLES32.glDeleteVertexArrays(1, vaos, 0);
                vao = 0;
            }
        }
    }
}
