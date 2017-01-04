package proj.eventcam;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.os.Environment;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class SendImageActivity extends AppCompatActivity implements View.OnClickListener {

    private ImageView img;
    private String filePath;
    private File imgFile;
    private RelativeLayout actLayout;
    private Button sendButton, backButton;
    private Uri imageUri;
    private Bitmap imgBmp;
    private Context context;
    private String photoSendResponse;
    private View view;
    private Bundle mainActivityBundle;

    @Override
    protected void onCreate ( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_send_image );

        // Use this Bundle to get the correct Event-key from the previous activity
        mainActivityBundle = getIntent().getExtras();

        photoSendResponse = "";

        // Use this context to make Toasts in the 'onPostExecute' method in the AsyncTask
        context = this;

        actLayout = ( RelativeLayout ) findViewById( R.id.activity_send_image_layout );

        // Buttons
        sendButton = ( Button ) findViewById( R.id.send_image_button );
        sendButton.setOnClickListener( this );
        backButton = ( Button ) findViewById( R.id.back_button );
        backButton.setOnClickListener( this );

        // Use this code segment to set the background of the activity to the image that the user
        // last saved
        filePath =
                Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_PICTURES )
                        .getAbsolutePath();
        imgFile = new File( filePath, "picture.jpg" );
        imgBmp = null;
        try {
            imgBmp = BitmapFactory.decodeStream( new FileInputStream( imgFile ) );
        } catch ( FileNotFoundException e ) {
            e.printStackTrace();
        }
        BitmapDrawable imgBmpDraw =
                new BitmapDrawable( getApplicationContext().getResources(), imgBmp );
        actLayout.setBackgroundDrawable( imgBmpDraw );
    }

    @Override
    public void onClick ( View v ) {
        view = v;
        switch ( v.getId() ) {
            case R.id.send_image_button :
                new SendImage( mainActivityBundle.getString( "eventKey" ),
                        mainActivityBundle.getString( "userName" ) ).execute( "SomeIP" ); // Removed original IP for privacy
                break;
            case R.id.back_button :
                takePhoto( v );
                break;
        }
    }

    // Same takePhoto method from the MainActivity
    private void takePhoto ( View v ) {
        Intent intent = new Intent( "android.media.action.IMAGE_CAPTURE" );
        File photo = new File(
                Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_PICTURES ),
                "picture.jpg"
        );
        imageUri = Uri.fromFile( photo );
        intent.putExtra( MediaStore.EXTRA_OUTPUT, imageUri );
        startActivityForResult( intent, MainActivity.TAKE_PICTURE );
    }

    // Use this method to start the correct activity after the user sends a photo
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
            } catch ( Exception e ) {
                Log.e( MainActivity.logtag, e.toString() );
            } finally {
                // Restart the SendImageActivity
                Intent sendImage = new Intent( SendImageActivity.this, SendImageActivity.class );
                sendImage.putExtra( "eventKey", mainActivityBundle.getString( "eventKey" ) );
                sendImage.putExtra( "userName", mainActivityBundle.getString( "userName" ) );
                startActivity( sendImage );
            }
        }
    }

    // This method changes a bitmap object into a byte array that can be sent
    // through the ObjectOutputStream to the Server
    private byte[] getImageArr ( Bitmap bitmap ) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress( Bitmap.CompressFormat.JPEG, 100, baos );
        if ( bitmap != null && !bitmap.isRecycled() ) {
            bitmap.isRecycled();
            bitmap = null;
        }
        return baos.toByteArray();
    }

    // AsyncTask class that does networking operations outside the UI Thread
    private class SendImage extends AsyncTask<String, Void, Void> {

        ProgressDialog verifying;

        String eventKey;
        String userName;

        boolean didNotSend;

        public SendImage ( String eventKey, String userName ) {
            this.eventKey = eventKey;
            this.userName = userName;
            didNotSend = false;
        }

        @Override
        protected void onPreExecute () {
            verifying = new ProgressDialog( context );
            verifying.setTitle( "Sending Image" );
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
                socket.setSoTimeout( 30000 ); // Set 30 seconds socket timeout for read() operations
                try {
                    OutputStream os = socket.getOutputStream();

                    // Use this to send the image file as an object
                    toSocket = new ObjectOutputStream( os );

                    toSocket.writeObject( "M" ); // M for mobile connection
                    toSocket.flush();
                    fromSocket = new ObjectInputStream( socket.getInputStream() );
                    fromSocket.readObject().toString();

                    toSocket.writeObject( this.eventKey ); // Send event-key again
                    toSocket.flush();
                    fromSocket.readObject().toString();

                    toSocket.writeObject( this.userName );
                    toSocket.flush();
                    fromSocket.readObject().toString();

                    // Now, send the photo and wait for successful send response from server

                    /* Send photo code goes here... */
                    toSocket.writeObject( getImageArr( imgBmp ) );
                    toSocket.flush();

                    photoSendResponse = fromSocket.readObject().toString();

                } catch ( InterruptedIOException e ) {
                    // Remote host timed out during read operation
                    e.printStackTrace();
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
            if ( !photoSendResponse.equals( "" ) ) { // If the server actually responded...

                if ( photoSendResponse.equals( "Received" ) ) {
                    /* ...and the server received the photo, bring the
                     * Prism app back to the device camera inner Activity
                     * so the user can take another photo. */
                    takePhoto( view );
                }

            } else {
                // In other words, the attempted sending of the photo timed out.
                Toast.makeText( context, "Image Send Failed", Toast.LENGTH_LONG ).show();
            }
        }

    }

}