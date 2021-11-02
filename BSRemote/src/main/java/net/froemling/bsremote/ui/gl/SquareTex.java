package net.froemling.bsremote.ui.gl;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

class SquareTex {

  // number of coordinates per vertex in this array
  private static final int COORDS_PER_VERTEX = 3;
  private static final int COORDS_PER_UV = 2;
  private static final float[] squareCoordinates = {
    -0.5f, 0.5f, 0.0f, // top left
    -0.5f, -0.5f, 0.0f, // bottom left
    0.5f, -0.5f, 0.0f, // bottom right
    0.5f, 0.5f, 0.0f
  }; // top right
  private static final float[] squareUVs = {1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f};
  private final FloatBuffer vertexBuffer;
  private final FloatBuffer uvBuffer;
  private final ShortBuffer drawListBuffer;
  private final int mProgram;
  private final short[] drawOrder = {0, 1, 2, 0, 2, 3}; // order to draw
  // vertices

  SquareTex() {
    // initialize vertex byte buffer for shape coordinates
    ByteBuffer bb =
        ByteBuffer.allocateDirect(
            // (# of coordinate values * 4 bytes per float)
            squareCoordinates.length * 4);
    bb.order(ByteOrder.nativeOrder());
    vertexBuffer = bb.asFloatBuffer();
    vertexBuffer.put(squareCoordinates);
    vertexBuffer.position(0);

    bb =
        ByteBuffer.allocateDirect(
            // (# of coordinate values * 4 bytes per float)
            squareUVs.length * 4);
    bb.order(ByteOrder.nativeOrder());
    uvBuffer = bb.asFloatBuffer();
    uvBuffer.put(squareUVs);
    uvBuffer.position(0);

    // initialize byte buffer for the draw list
    ByteBuffer dlb =
        ByteBuffer.allocateDirect(
            // (# of coordinate values * 2 bytes per short)
            drawOrder.length * 2);
    dlb.order(ByteOrder.nativeOrder());
    drawListBuffer = dlb.asShortBuffer();
    drawListBuffer.put(drawOrder);
    drawListBuffer.position(0);

    // prepare shaders and OpenGL program
    String vertexShaderCode =
        "uniform mat4 uMVPMatrix;"
            + "attribute vec4 "
            + "vPosition;"
            + "attribute vec2 uv;"
            + "varying vec2 vUV;\n"
            + "void main"
            + "() "
            + "{"
            +
            // the matrix must be included as a modifier of gl_Position
            "  gl_Position = vPosition * uMVPMatrix;"
            + "  vUV = uv;"
            + "}";
    int vertexShader = GLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
    String fragmentShaderCode =
        "precision mediump float;"
            + "uniform vec4 "
            + "vColor;"
            + "uniform "
            + "sampler2D tex;"
            + "varying mediump vec2 vUV;"
            + "void main() {"
            + "  gl_FragColor = vColor * (texture2D"
            + "(tex,"
            + "vUV));"
            + "}";
    int fragmentShader = GLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

    mProgram = GLES20.glCreateProgram(); // create empty OpenGL
    // Program
    GLES20.glAttachShader(mProgram, vertexShader); // add the vertex shader
    // to program
    GLES20.glAttachShader(mProgram, fragmentShader); // add the fragment
    // shader to program
    GLES20.glLinkProgram(mProgram); // create OpenGL program
    // executables
  }

  void draw(float[] mvpMatrix, float r, float g, float b, int tex) {
    // Add program to OpenGL environment
    GLES20.glUseProgram(mProgram);

    // get handle to vertex shader's vPosition member
    int mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");

    // Enable a handle to the triangle vertices
    GLES20.glEnableVertexAttribArray(mPositionHandle);

    // Prepare the triangle coordinate data
    int vertexStride = COORDS_PER_VERTEX * 4;
    GLES20.glVertexAttribPointer(
        mPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);

    // get handle to vertex shader's vPosition member
    int mUVHandle = GLES20.glGetAttribLocation(mProgram, "uv");
    GLRenderer.checkGlError("glGetUniformLocation");

    // Enable a handle to the triangle vertices
    GLES20.glEnableVertexAttribArray(mUVHandle);

    // Prepare the triangle coordinate data
    int uvStride = COORDS_PER_UV * 4;
    GLES20.glVertexAttribPointer(
        mUVHandle, COORDS_PER_UV, GLES20.GL_FLOAT, false, uvStride, uvBuffer);

    // get handle to fragment shader's vColor member
    int mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");
    GLRenderer.checkGlError("glGetUniformLocation");

    // Set color for drawing the triangle
    float[] color = {r, g, b, 1.0f};
    GLES20.glUniform4fv(mColorHandle, 1, color, 0);

    int mTexHandle = GLES20.glGetUniformLocation(mProgram, "tex");
    GLRenderer.checkGlError("glGetUniformLocation");
    GLES20.glUniform1i(mTexHandle, 0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
    GLRenderer.checkGlError("glBindTexture");

    // get handle to shape's transformation matrix
    int mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
    GLRenderer.checkGlError("glGetUniformLocation");
    // Apply the projection and view transformation
    GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);
    GLRenderer.checkGlError("glUniformMatrix4fv");

    // Draw the square
    GLES20.glDrawElements(
        GLES20.GL_TRIANGLES, drawOrder.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

    // Disable vertex array
    GLES20.glDisableVertexAttribArray(mPositionHandle);
  }
}
