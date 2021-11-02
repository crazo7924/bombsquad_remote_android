/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.froemling.bsremote.ui.gl;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

class Triangle {

  // number of coordinates per vertex in this array
  private static final float[] triangleCoordinates = { // in counterclockwise order:
    0.0f, 0.622008459f, 0.0f, // top
    -0.5f, -0.311004243f, 0.0f, // bottom left
    0.5f, -0.311004243f, 0.0f // bottom right
  };

  Triangle() {
    // initialize vertex byte buffer for shape coordinates
    ByteBuffer bb =
        ByteBuffer.allocateDirect(
            // (number of coordinate values * 4 bytes per float)
            triangleCoordinates.length * 4);
    // use the device hardware's native byte order
    bb.order(ByteOrder.nativeOrder());

    // create a floating point buffer from the ByteBuffer
    FloatBuffer vertexBuffer = bb.asFloatBuffer();
    // add the coordinates to the FloatBuffer
    vertexBuffer.put(triangleCoordinates);
    // set the buffer to read the first coordinate
    vertexBuffer.position(0);

    // prepare shaders and OpenGL program
    String vertexShaderCode =
        "uniform mat4 uMVPMatrix;"
            + "attribute vec4 vPosition;"
            + "void main() {"
            +
            // the matrix must be included as a modifier of gl_Position
            "  gl_Position = vPosition * uMVPMatrix;"
            + "}";
    int vertexShader = GLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
    String fragmentShaderCode =
        "precision mediump float;"
            + "uniform vec4 "
            + "vColor;"
            + "void "
            + "main() {"
            + "  gl_FragColor = vColor;"
            + "}";
    int fragmentShader = GLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

    int mProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(mProgram, vertexShader); // add the vertex shader
    // to program
    GLES20.glAttachShader(mProgram, fragmentShader); // add the fragment
    // shader to program
    GLES20.glLinkProgram(mProgram); // create OpenGL program
    // executables

  }
}
