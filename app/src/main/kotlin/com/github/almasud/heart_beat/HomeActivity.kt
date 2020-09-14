package com.github.almasud.heart_beat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import kotlinx.android.synthetic.main.activity_home.*

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this@HomeActivity, HeartBeatActivity::class.java))
            finish()
        }
        btnCameraGrant.setOnClickListener {
            Dexter.withContext(this@HomeActivity)
                    .withPermission(Manifest.permission.CAMERA)
                    .withListener(object : PermissionListener {
                        override fun onPermissionGranted(response: PermissionGrantedResponse) {
                            startActivity(Intent(this@HomeActivity, HeartBeatActivity::class.java))
                            finish()
                        }

                        override fun onPermissionDenied(response: PermissionDeniedResponse) {
                            if (response.isPermanentlyDenied) {
                                val builder = AlertDialog.Builder(this@HomeActivity)
                                builder.setTitle("Permission Denied.")
                                        .setMessage("Permission to access device camera is permanently denied. You can go to settings to allow permission.")
                                        .setNegativeButton("Cancel", null)
                                        .setPositiveButton("OK") { dialog, which ->
                                            val intent = Intent()
                                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                            intent.data = Uri.fromParts("package", packageName, null)
                                            startActivity(intent)
                                        }.show()
                            } else {
                                Toast.makeText(this@HomeActivity, "Permission denied.", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest, token: PermissionToken) {
                            token.continuePermissionRequest()
                        }
                    }).check()
        }
    }
}