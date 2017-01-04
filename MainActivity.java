package proj.eventcam;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    public static String logtag = "EventCam";
    public static int TAKE_PICTURE = 1888;
    private Uri imageUri;
    private Context context;
    private EditText eventKeyET;
    private EditText userNameET;
    private String verificationResponse;
    private View view;

    @Override
    protected void onCreate ( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        // Use this to make Toasts in the 'onPostExecute' method of the AsyncTask
        context = this;

        // EditTexts that the user can interact with on the Login Screen
        eventKeyET = ( EditText ) findViewById( R.id.event_code );
        userNameET = ( EditText ) findViewById( R.id.user_name );

        Button cameraButton = ( Button ) findViewById( R.id.button_camera );
        cameraButton.setOnClickListener( cameraListener );

        verificationResponse = "";
    }

    private OnClickListener cameraListener = new OnClickListener() {
        @Override
        public void onClick ( View v ) {
            //takePhoto( v );
            view = v;

            if ( eventKeyET.getText().toString().equals( "" )
                    || userNameET.getText().toString().equals( "" ) ) {

                Toast.makeText( MainActivity.this, "Enter your Event-key and Username",
                        Toast.LENGTH_LONG ).show();

            } else {
                // Begin sending the Event-key to the Server
                new VerifyEventCode( eventKeyET.getText().toString(),
                        userNameET.getText().toString() ).execute( "192.168.1.101" ); // 192.168.0.4

            }
        }
    };

    // Use this method to start the built-in device camera app from within Prism
    private void takePhoto ( View v ) {
        // Start the built-in device camera app
        Intent intent = new Intent( "android.media.action.IMAGE_CAPTURE" );
        File photo = new File(
                Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_PICTURES ),
                "picture.jpg"
        );
        imageUri = Uri.fromFile( photo );
        intent.putExtra( MediaStore.EXTRA_OUTPUT, imageUri );
        startActivityForResult( intent, MainActivity.TAKE_PICTURE );
    }

    @Override
    protected void onActivityResult ( int requestCode, int resultCode, Intent intent ) {
        super.onActivityResult( requestCode, resultCode, intent );
        if ( resultCode == Activity.RESULT_OK ) {
            Uri selectedImage = imageUri;
            getContentResolver().notifyChange( selectedImage, null );

            ImageView imageView = ( ImageView ) findViewById( R.id.image_camera );
            ContentResolver cr = getContentResolver();
            Bitmap bitmap;

            try {
                bitmap = MediaStore.Images.Media.getBitmap( cr, selectedImage );
                imageView.setImageBitmap( bitmap );
                //Toast.makeText( MainActivity.this, "Hi Everybody", Toast.LENGTH_LONG ).show();
            } catch ( Exception e ) {
                Log.e( logtag, e.toString() );
            } finally {
                // Start the SendImageActivity after the user takes a photo
                Intent sendImage = new Intent( MainActivity.this, SendImageActivity.class );
                sendImage.putExtra( "eventKey", eventKeyET.getText().toString() );
                sendImage.putExtra( "userName", userNameET.getText().toString() );
                startActivity( sendImage );
            }
        }
    }

    // AsyncTask class for use with networking operations outside the UI Thread
    private class VerifyEventCode extends AsyncTask<String, Void, Void> {

        ProgressDialog verifying;

        String eventKey;
        String userName;

        public VerifyEventCode ( String eventKey, String userName ) {
            this.eventKey = eventKey;
            this.userName = userName;
        }

        @Override
        protected void onPreExecute () {
            verifying = new ProgressDialog( context );
            verifying.setTitle( "Verifying Event-key" );
            verifying.show();
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground ( String... params ) {

            int port = 80; // Port number to connect through
            Socket socket = null;
            ObjectOutputStream toSocket = null;
            ObjectInputStream fromSocket = null;
            try {
                // Create a new InetAddress to hold the IP Address for the Server
                InetAddress ip = InetAddress.getByName( params[0] );
                socket = new Socket( ip, port );
                toSocket = new ObjectOutputStream( socket.getOutputStream() );
                toSocket.flush();

                toSocket.writeObject( "M" ); // M for mobile
                toSocket.flush();

                // Use this to send objects to the Server
                fromSocket = new ObjectInputStream( socket.getInputStream() );

                fromSocket.readObject().toString();

                // Send the Event-key to the Server for verification
                toSocket.writeObject( this.eventKey );
                toSocket.flush();
                verificationResponse = fromSocket.readObject().toString();

                // Make sure the Event-key was valid before attempting to send the
                // username to the Server
                if ( verificationResponse.equals( "Validated" ) ) {
                    toSocket.writeObject( this.userName );
                    toSocket.flush();
                    fromSocket.readObject().toString();
                }

            } catch ( UnknownHostException e ) {
                e.printStackTrace();
            } catch ( IOException e ) {
                e.printStackTrace();
            } catch ( ClassNotFoundException e ) {
                e.printStackTrace();
            } finally {
                if ( socket != null && toSocket != null ) {
                    try {
                        // Safely close the connection with the Server
                        toSocket.writeObject( "exit" );
                        fromSocket.close();
                        toSocket.close();
                        socket.close();
                    } catch ( IOException e ) {
                        e.printStackTrace();
                    }
                }
            }

            return null;

        }

        @Override
        protected void onPostExecute ( Void result ) {
            verifying.dismiss();
            super.onPostExecute( result );
            if ( !verificationResponse.equals( "" ) ) { // If the server actually responded...

                if ( verificationResponse.equals( "Validated" ) ) {

                    /* If the server response says 'Validated', then the user
                     * entered the correct Event-key. The Prism app should now be
                     * forwarded to the device camera inner Activity for taking photos. */
                    takePhoto( view );

                } else if ( verificationResponse.equals( "Invalid" ) ) {

                    // If the server responds that the Event-key was invalid...
                    Toast.makeText( context, "Invalid Event-key, Try Again",
                            Toast.LENGTH_LONG ).show();

                    // ...reset the Event-key field so the user can reenter it
                    eventKeyET.setText( "" );
                    userNameET.setText( "" );

                }
            } else {
                Toast.makeText( context, "Did not receive server response...",
                        Toast.LENGTH_LONG ).show();
            }
        }

    }

}
