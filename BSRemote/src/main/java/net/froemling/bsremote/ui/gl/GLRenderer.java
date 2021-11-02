package net.froemling.bsremote.ui.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import net.froemling.bsremote.R;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLRenderer implements GLSurfaceView.Renderer {

  private static final String TAG = "BSRemoteGLRenderer";
  private final float[] mMVPMatrix = new float[16];
  private final float[] mProjMatrix = new float[16];
  private final float[] mVMatrix = new float[16];
  private final Context _context;
  volatile float quitButtonX;
  volatile float quitButtonY;
  volatile float quitButtonWidth;
  volatile float quitButtonHeight;
  volatile boolean quitButtonPressed;
  volatile float prefsButtonX;
  volatile float prefsButtonY;
  volatile float prefsButtonWidth;
  volatile float prefsButtonHeight;
  volatile boolean prefsButtonPressed;
  volatile float startButtonX;
  volatile float startButtonY;
  volatile float startButtonWidth;
  volatile float startButtonHeight;
  volatile boolean startButtonPressed;
  volatile float bombButtonX;
  volatile float bombButtonY;
  volatile float bombButtonWidth;
  volatile float bombButtonHeight;
  volatile float punchButtonX;
  volatile float punchButtonY;
  volatile float punchButtonWidth;
  volatile float punchButtonHeight;
  volatile float throwButtonX;
  volatile float throwButtonY;
  volatile float throwButtonWidth;
  volatile float throwButtonHeight;
  volatile float jumpButtonX;
  volatile float jumpButtonY;
  volatile float jumpButtonWidth;
  volatile float jumpButtonHeight;
  volatile float joystickCenterX;
  volatile float joystickCenterY;
  volatile float joystickWidth;
  volatile float joystickHeight;
  volatile float joystickX;
  volatile float joystickY;
  volatile boolean jumpPressed;
  volatile boolean punchPressed;
  volatile boolean throwPressed;
  volatile boolean bombPressed;
  volatile boolean thumbPressed;
  private Square mSquare;
  private SquareTex mSquareTex;
  private int _bgTex;
  private int _buttonBombTex;
  private int _buttonBombPressedTex;
  private int _buttonJumpTex;
  private int _buttonJumpPressedTex;
  private int _buttonPunchTex;
  private int _buttonPunchPressedTex;
  private int _buttonThrowTex;
  private int _buttonThrowPressedTex;
  private int _centerTex;
  private int _thumbTex;
  private int _thumbPressedTex;
  private int _buttonLeaveTex;
  private int _buttonSettingsTex;
  private int _buttonStartTex;
  private float _ratio = 1.0f;

  GLRenderer(Context c) {
    _context = c;
  }

  // Get a new texture id:
  private static int newTextureID() {
    int[] temp = new int[1];
    GLES20.glGenTextures(1, temp, 0);
    return temp[0];
  }

  static int loadShader(int type, String shaderCode) {

    // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
    // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
    int shader = GLES20.glCreateShader(type);

    // add the source code to the shader and compile it
    GLES20.glShaderSource(shader, shaderCode);
    GLES20.glCompileShader(shader);

    return shader;
  }

  /**
   * Utility method for debugging OpenGL calls. Provide the name of the call just after making it:
   *
   * <pre>
   * mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");
   * MyGLRenderer.checkGlError("glGetUniformLocation");</pre>
   *
   * <p>If the operation is not successful, the check throws an error.
   *
   * @param glOperation - Name of the OpenGL call to check.
   */
  static void checkGlError(String glOperation) {
    int error;
    if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      Log.e(TAG, glOperation + ": glError " + error);
      throw new RuntimeException(glOperation + ": glError " + error);
    }
  }

  // Will load a texture out of a drawable resource file, and return an
  // OpenGL texture ID:
  private int loadTexture(int resource) {

    // In which ID will we be storing this texture?
    int id = newTextureID();

    // We need to flip the textures vertically:
    android.graphics.Matrix flip = new android.graphics.Matrix();
    flip.postScale(1f, -1f);

    // This will tell the BitmapFactory to not scale based on the device's
    // pixel density:
    // (Thanks to Matthew Marshall for this bit)
    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inScaled = false;

    // Load up, and flip the texture:
    Bitmap temp = BitmapFactory.decodeResource(_context.getResources(), resource, opts);
    Bitmap bmp = Bitmap.createBitmap(temp, 0, 0, temp.getWidth(), temp.getHeight(), flip, true);
    temp.recycle();

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);

    // Set all of our texture parameters:
    GLES20.glTexParameterf(
        GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

    // Generate, and load up all of the mipmaps:
    for (int level = 0, height = bmp.getHeight(), width = bmp.getWidth(); true; level++) {
      // Push the bitmap onto the GPU:
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, level, bmp, 0);

      // We need to stop when the texture is 1x1:
      if (height == 1 && width == 1) {
        break;
      }

      // Resize, and let's go again:
      width >>= 1;
      height >>= 1;
      if (width < 1) {
        width = 1;
      }
      if (height < 1) {
        height = 1;
      }

      Bitmap bmp2 = Bitmap.createScaledBitmap(bmp, width, height, true);
      bmp.recycle();
      bmp = bmp2;
    }
    bmp.recycle();

    return id;
  }

  @Override
  public void onSurfaceCreated(GL10 unused, EGLConfig config) {

    // Set the background frame color
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

    new Triangle();
    mSquare = new Square();
    mSquareTex = new SquareTex();

    // load our textures
    _bgTex = loadTexture(R.drawable.controllerbg);
    checkGlError("loadTexture");
    _buttonBombTex = loadTexture(R.drawable.button_bomb);
    checkGlError("loadTexture");
    _buttonBombPressedTex = loadTexture(R.drawable.button_bomb_pressed);
    checkGlError("loadTexture");
    _buttonJumpTex = loadTexture(R.drawable.button_jump);
    checkGlError("loadTexture");
    _buttonJumpPressedTex = loadTexture(R.drawable.button_jump_pressed);
    checkGlError("loadTexture");
    _buttonPunchTex = loadTexture(R.drawable.button_punch);
    checkGlError("loadTexture");
    _buttonPunchPressedTex = loadTexture(R.drawable.button_punch_pressed);
    checkGlError("loadTexture");
    _buttonThrowTex = loadTexture(R.drawable.button_throw);
    checkGlError("loadTexture");
    _buttonThrowPressedTex = loadTexture(R.drawable.button_throw_pressed);
    checkGlError("loadTexture");
    _centerTex = loadTexture(R.drawable.center);
    checkGlError("loadTexture");
    _thumbTex = loadTexture(R.drawable.thumb);
    checkGlError("loadTexture");
    _thumbPressedTex = loadTexture(R.drawable.thumb_pressed);
    checkGlError("loadTexture");
    _buttonStartTex = loadTexture(R.drawable.button_start);
    checkGlError("loadTexture");
    _buttonLeaveTex = loadTexture(R.drawable.button_leave);
    checkGlError("loadTexture");
    _buttonSettingsTex = loadTexture(R.drawable.button_settings);
    checkGlError("loadTexture");
  }

  private void _drawBG(int tex) {
    float[] m = new float[16];
    Matrix.setIdentityM(m, 0);
    Matrix.scaleM(m, 0, -2.0f, 2.0f, 1.0f);
    if (tex == -1) {
      mSquare.draw(m, (float) 1, (float) 1, (float) 1);
    } else {
      mSquareTex.draw(m, (float) 1, (float) 1, (float) 1, _bgTex);
    }
    checkGlError("draw");
  }

  private void _drawBox(float x, float y, float sx, float sy, float r, float g, float b, int tex) {
    // scale and translate
    float[] m = new float[16];
    Matrix.setIdentityM(m, 0);
    Matrix.scaleM(m, 0, 2.0f * sx, 2.0f * sy, 1.0f);
    m[3] += (1.0 - 2.0 * x);
    m[7] += (1.0 / _ratio - 2.0 * y);
    float[] m2 = new float[16];
    Matrix.multiplyMM(m2, 0, m, 0, mMVPMatrix, 0);
    if (tex == -1) {
      mSquare.draw(m2, r, g, b);
    } else {
      mSquareTex.draw(m2, r, g, b, tex);
    }
    checkGlError("draw");
  }

  @Override
  public void onDrawFrame(GL10 unused) {

    // Draw background color
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

    // Set the camera position (View matrix)
    Matrix.setLookAtM(mVMatrix, 0, 0, 0, -1, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

    // Calculate the projection and view transformation
    Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mVMatrix, 0);

    GLES20.glDisable(GLES20.GL_BLEND);
    _drawBG(_bgTex);

    // actual graphics
    GLES20.glEnable(GLES20.GL_BLEND);
    GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    float bs = 2.8f;
    _drawBox(
        bombButtonX,
        bombButtonY,
        bombButtonWidth * bs,
        bombButtonHeight * bs,
        1,
        1,
        1,
        bombPressed ? _buttonBombPressedTex : _buttonBombTex);
    _drawBox(
        punchButtonX,
        punchButtonY,
        punchButtonWidth * bs,
        punchButtonHeight * bs,
        1,
        1,
        1,
        punchPressed ? _buttonPunchPressedTex : _buttonPunchTex);
    _drawBox(
        jumpButtonX,
        jumpButtonY,
        jumpButtonWidth * bs,
        jumpButtonHeight * bs,
        1,
        1,
        1,
        jumpPressed ? _buttonJumpPressedTex : _buttonJumpTex);
    _drawBox(
        throwButtonX,
        throwButtonY,
        throwButtonWidth * bs,
        throwButtonHeight * bs,
        1,
        1,
        1,
        throwPressed ? _buttonThrowPressedTex : _buttonThrowTex);

    float cs = 2.2f;
    _drawBox(
        joystickCenterX,
        joystickCenterY,
        joystickWidth * cs,
        joystickHeight * cs,
        1,
        1,
        1,
        _centerTex);

    float ts = 0.9f;
    _drawBox(
        joystickX,
        joystickY,
        joystickWidth * ts,
        joystickHeight * ts,
        1,
        1,
        1,
        thumbPressed ? _thumbPressedTex : _thumbTex);

    float tbsx = 1.1f;
    float tbsy = 1.6f;
    float tboy = 0.15f * quitButtonHeight * tbsy;

    float b;
    b = quitButtonPressed ? 2.0f : 1.0f;
    _drawBox(
        quitButtonX,
        quitButtonY + tboy,
        quitButtonWidth * tbsx,
        quitButtonHeight * tbsy,
        b,
        b,
        b,
        _buttonLeaveTex);
    b = prefsButtonPressed ? 2.0f : 1.0f;
    _drawBox(
        prefsButtonX,
        prefsButtonY + tboy,
        prefsButtonWidth * tbsx,
        prefsButtonHeight * tbsy,
        b,
        b,
        b,
        _buttonSettingsTex);
    b = startButtonPressed ? 2.0f : 1.0f;
    _drawBox(
        startButtonX,
        startButtonY + tboy,
        startButtonWidth * tbsx,
        startButtonHeight * tbsy,
        b,
        b,
        b,
        _buttonStartTex);
  }

  @Override
  public void onSurfaceChanged(GL10 unused, int width, int height) {
    // Adjust the viewport based on geometry changes,
    // such as screen rotation
    GLES20.glViewport(0, 0, width, height);

    _ratio = (float) width / height;
    // Log.v(TAG,"SETTING RATIO "+_ratio);
    // this projection matrix is applied to object coordinates
    // in the onDrawFrame() method
    // Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
    Matrix.orthoM(mProjMatrix, 0, -1, 1, -1 / _ratio, 1 / _ratio, -1, 1);
    // printMat(mProjMatrix,"ORTHO");
  }
}
