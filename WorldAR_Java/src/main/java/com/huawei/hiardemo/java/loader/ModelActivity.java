package com.huawei.hiardemo.java.loader;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import org.andresoviedo.util.android.ContentUtils;

import java.io.IOException;

/**
 * This activity represents the container for our 3D viewer.
 *
 * @author andresoviedo
 */
public class ModelActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_LOAD_TEXTURE = 1000;

    /**
     * Type of model if file name has no extension (provided though content provider)
     */
    private int paramType = -1;
    /**
     * The file to load. Passed as input parameter
     */
//    private Uri paramUri = Uri.parse("assets://" + getPackageName() + "/models/cube.obj");
    private Uri paramUri;
    /**
     * Enter into Android Immersive mode so the renderer is full screen or not
     */
    private boolean immersiveMode = true;
    /**
     * Background GL clear color. Default is light gray
     */
    private float[] backgroundColor = new float[]{0.2f, 0.2f, 0.2f, 1.0f};

    protected ModelSurfaceView glView;

    private SceneLoader scene;

    private Handler handler;

//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        // Try to get input parameters
//        Bundle b = getIntent().getExtras();
//        if (b != null) {
//            if (b.getString("uri") != null) {
//                this.paramUri = Uri.parse(b.getString("uri"));
//            }
//            this.paramType = b.getString("type") != null ? Integer.parseInt(b.getString("type")) : -1;
//            this.immersiveMode = "true".equalsIgnoreCase(b.getString("immersiveMode"));
//            try {
//                String[] backgroundColors = b.getString("backgroundColor").split(" ");
//                backgroundColor[0] = Float.parseFloat(backgroundColors[0]);
//                backgroundColor[1] = Float.parseFloat(backgroundColors[1]);
//                backgroundColor[2] = Float.parseFloat(backgroundColors[2]);
//                backgroundColor[3] = Float.parseFloat(backgroundColors[3]);
//            } catch (Exception ex) {
//                // Assuming default background color
//            }
//        }
//        Log.i("Renderer", "Params: uri '" + paramUri + "'");
//
//        handler = new Handler(getMainLooper());
//
//        // Create our 3D sceneario
//        if (paramUri == null) {
//            scene = new ExampleSceneLoader(this);
//        } else {
//            scene = new SceneLoader(this);
//        }
//        scene.init();
//
//        // Create a GLSurfaceView instance and set it
//        // as the ContentView for this Activity.
//        gLView = new ModelSurfaceView(this);
//        setContentView(gLView);
//
//        // Show the Up button in the action bar.
//        setupActionBar();
//
//        // TODO: Alert user when there is no multitouch support (2 fingers). He won't be able to rotate or zoom
//        ContentUtils.printTouchCapabilities(getPackageManager());
//
//        setupOnSystemVisibilityChangeListener();
//    }

    public void initScene() {
//        ContentUtils.addUri("cube.mtl", Uri.parse("assets://" + getPackageName() + "/models/cube.mtl"));
        paramUri = Uri.parse("assets://" + getPackageName() + "/models/cowboy.dae");
        ContentUtils.addUri("cowboy.png", Uri.parse("assets://" + getPackageName() + "/models/cowboy.png"));

        handler = new Handler(getMainLooper());

        scene = new SceneLoader(this);
        scene.init();
    }

    public Uri getParamUri() {
        return paramUri;
    }

    public int getParamType() {
        return paramType;
    }

    public float[] getBackgroundColor() {
        return backgroundColor;
    }

    public SceneLoader getScene() {
        return scene;
    }

    public ModelSurfaceView getGLView() {
        return glView;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case REQUEST_CODE_LOAD_TEXTURE:
                // The URI of the selected file
                final Uri uri = data.getData();
                if (uri != null) {
                    Log.i("ModelActivity", "Loading texture '" + uri + "'");
                    try {
                        ContentUtils.setThreadActivity(this);
                        scene.loadTexture(null, uri);
                    } catch (IOException ex) {
                        Log.e("ModelActivity", "Error loading texture: " + ex.getMessage(), ex);
                        Toast.makeText(this, "Error loading texture '" + uri + "'. " + ex
                                .getMessage(), Toast.LENGTH_LONG).show();
                    } finally {
                        ContentUtils.setThreadActivity(null);
                    }
                }
        }
    }
}
