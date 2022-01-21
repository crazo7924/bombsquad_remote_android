package net.froemling.bsremote.ui.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

internal class Square {
    private val vertexBuffer: FloatBuffer
    private val drawListBuffer: ShortBuffer
    private val mProgram: Int
    private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3) // order to draw
    fun draw(mvpMatrix: FloatArray?, r: Float, g: Float, b: Float) {
        // Add program to OpenGL environment
        GLES20.glUseProgram(mProgram)

        // get handle to vertex shader's vPosition member
        val mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mPositionHandle)

        // Prepare the triangle coordinate data
        val vertexStride = COORDS_PER_VERTEX * 4
        GLES20.glVertexAttribPointer(
            mPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer)

        // get handle to fragment shader's vColor member
        val mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor")

        // Set color for drawing the triangle
        val color = floatArrayOf(r, g, b, 1.0f)
        GLES20.glUniform4fv(mColorHandle, 1, color, 0)

        // get handle to shape's transformation matrix
        val mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        GLRenderer.checkGlError("glGetUniformLocation")

        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLRenderer.checkGlError("glUniformMatrix4fv")

        // Draw the square
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle)
    }

    companion object {
        // number of coordinates per vertex in this array
        private const val COORDS_PER_VERTEX = 3
        private val squareCoordinates = floatArrayOf(
            -0.5f, 0.5f, 0.0f,  // top left
            -0.5f, -0.5f, 0.0f,  // bottom left
            0.5f, -0.5f, 0.0f,  // bottom right
            0.5f, 0.5f, 0.0f
        ) // top right
    }

    // vertices
    init {
        // initialize vertex byte buffer for shape coordinates
        val bb = ByteBuffer.allocateDirect( // (# of coordinate values * 4 bytes per float)
            squareCoordinates.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(squareCoordinates)
        vertexBuffer.position(0)

        // initialize byte buffer for the draw list
        val dlb = ByteBuffer.allocateDirect( // (# of coordinate values * 2 bytes per short)
            drawOrder.size * 2)
        dlb.order(ByteOrder.nativeOrder())
        drawListBuffer = dlb.asShortBuffer()
        drawListBuffer.put(drawOrder)
        drawListBuffer.position(0)

        // prepare shaders and OpenGL program
        val vertexShaderCode = ("uniform mat4 uMVPMatrix;"
                + "attribute vec4 vPosition;"
                + "void main() {"
                +  // the matrix must be included as a modifier of gl_Position
                "  gl_Position = vPosition * uMVPMatrix;"
                + "}")
        val vertexShader = GLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShaderCode = ("precision mediump float;"
                + "uniform vec4 "
                + "vColor;"
                + "void "
                + "main() {"
                + "  gl_FragColor = vColor;"
                + "}")
        val fragmentShader = GLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        mProgram = GLES20.glCreateProgram() // create empty OpenGL
        // Program
        GLES20.glAttachShader(mProgram, vertexShader) // add the vertex shader
        // to program
        GLES20.glAttachShader(mProgram, fragmentShader) // add the fragment
        // shader to program
        GLES20.glLinkProgram(mProgram) // create OpenGL program
        // executables
    }
}