package com.example.quickaihelper

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val actionButton = Button(this).apply {
            text = "KÍCH HOẠT CHATBOX THÔNG BÁO"
            textSize = 16f
            setPadding(32, 24, 32, 24)
            setOnClickListener { verifyAndLaunch() }
        }

        layout.addView(actionButton)
        setContentView(layout)

        initNotificationChannel()
    }

    private fun verifyAndLaunch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
                return
            }
        }
        publishChatNotification()
        Toast.makeText(this, "Đã kích hoạt ô nhập trên thanh thông báo!", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            publishChatNotification()
            Toast.makeText(this, "Đã kích hoạt ô nhập trên thanh thông báo!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun publishChatNotification() {
        val remoteInput = RemoteInput.Builder(AnswerReceiver.KEY_TEXT_INPUT)
            .setLabel("Dán câu hỏi trắc nghiệm hoặc văn bản vào đây...")
            .build()

        val receiverIntent = Intent(this, AnswerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            receiverIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "Nhập / Dán câu hỏi",
            pendingIntent
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(this, AnswerReceiver.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_help)
            .setContentTitle("Trợ lý AI Trắc nghiệm")
            .setContentText("Bấm 'Nhập / Dán câu hỏi' bên dưới để giải tức thì.")
            .setOngoing(true)
            .addAction(replyAction)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(AnswerReceiver.NOTIFICATION_ID, notification)
    }

    private fun initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AnswerReceiver.CHANNEL_ID,
                "Trợ lý AI Thông Báo",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kênh gửi nhận lời giải trực tiếp qua notification"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
