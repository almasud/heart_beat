package com.github.almasud.heart_beat

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.almasud.heart_beat.lib.HeartBeat
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_heart_beat.*
import kotlinx.android.synthetic.main.toolbar.view.*
import net.kibotu.kalmanrx.jama.Matrix
import net.kibotu.kalmanrx.jkalman.JKalman


class HeartBeatActivity : AppCompatActivity() {
    private var subscription: CompositeDisposable? = null
    private companion object {
        const val TAG = "HeartBeatActivity"
        const val MAX_PROGRESS = 5
        const val MIN_PROGRESS = 0
        var mBpPulse = false
        var mXAxis = 0f
        @Volatile
        var mProgressStatus = false
    }
    private var mEntries = ArrayList<Entry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_beat)

        // Set a toolbar
        toolbarHeartBeat.tvToolbarHome.text = resources.getString(R.string.app_name)
        toolbarHeartBeat.ivCrossToolbarHome.setImageResource(R.drawable.cross_mark)
        toolbarHeartBeat.ivCrossToolbarHome.setOnClickListener {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun startMeasureHeartBeat() {
        val kalman = JKalman(2, 1)

        // measurement [x]
        val m = Matrix(1, 1)

        // transitions for x, dx
        val tr = arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0))
        kalman.transition_matrix = Matrix(tr)

        // 1s somewhere?
        kalman.error_cov_post = kalman.error_cov_post.identity()


        val bpmUpdates = HeartBeat()
                .withAverageAfterSeconds(3)
                .setFingerDetectionListener(this::onFingerChange)
                .bpmUpdates(surfaceCameraHome)
                .subscribe({

                    if (it.value == 0)
                        return@subscribe

                    m.set(0, 0, it.value.toDouble())

                    // state [x, dx]
                    val s = kalman.Predict()

                    // corrected state [x, dx]
                    val c = kalman.Correct(m)

                    val bpm = it.copy(value = c.get(0, 0).toInt())
                    Log.v("HeartBeat", "[onBpm] ${it.value} => ${bpm.value}")
                    onBpm(bpm)
                }, Throwable::printStackTrace)

        subscription?.add(bpmUpdates)
    }

    override fun onResume() {
        super.onResume()

        dispose()
        subscription = CompositeDisposable()

        startMeasureHeartBeat()
    }

    override fun onPause() {
        dispose()
        super.onPause()
    }

    private fun dispose() {
        if (subscription?.isDisposed == false)
            subscription?.dispose()
    }

    @SuppressLint("SetTextI18n")
    private fun onBpm(bpm: HeartBeat.Bpm) {
        // Log.v("HeartBeat", "[onBpm] $bpm")
        tvHeartBreathHome.text = bpm.value.toString()

        mBpPulse = if (mBpPulse) {
            ivHeartBreathHome.setImageResource(R.drawable.heart_icon)
            false
        } else {
            ivHeartBreathHome.setImageResource(R.drawable.heart_icon_fill)
            true
        }

        mEntries.add(Entry(mXAxis++, bpm.value.toFloat()))
        val lineDataSet = LineDataSet(mEntries, "Heart Beat")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            lineDataSet.color = resources.getColor(R.color.colorAccent, null)
        } else {
            lineDataSet.color = resources.getColor(R.color.colorAccent)
        }
        lineDataSet.setDrawValues(false)
        lineDataSet.setDrawCircles(false)

        val lineData = LineData(lineDataSet)
        lineChartBreathHome.data = lineData

        // Style chart
        lineChartBreathHome.description = null
        lineChartBreathHome.setDrawGridBackground(false)
        lineChartBreathHome.setDrawBorders(false)
        lineChartBreathHome.isAutoScaleMinMaxEnabled = true

        // Remove axis
        lineChartBreathHome.axisLeft.isEnabled = false
        lineChartBreathHome.axisRight.isEnabled = false
        lineChartBreathHome.xAxis.isEnabled = false
        lineChartBreathHome.legend.isEnabled = false

        lineChartBreathHome.invalidate() // refresh
    }

    private fun onFingerChange(fingerDetected: Boolean){
        if (!fingerDetected) {
            Log.d(TAG, "onFingerChange: Finger is not detected.")
            viewBreathInIVSelectHome.visibility = View.GONE
            viewBreathOutIVSelectHome.visibility = View.GONE
            viewGrayBreathInHome.visibility = View.VISIBLE
            viewGrayBreathOutHome.visibility = View.VISIBLE

            mProgressStatus = false
            progressBarHome.progress = 0
            tvProgressMessageHome.text = resources.getString(R.string.place_finger_on_the_camera)
        } else {
            Log.d(TAG, "onFingerChange: Finger detected.")
            // Reset values
            mEntries.clear()
            tvHeartBreathHome.text = "0"
            mProgressStatus = true
            tvProgressMessageHome.text = resources.getString(R.string.inhale_slowly_for_five_seconds)

            var progress = 0
            Thread {
                Log.d(TAG, "onFingerChange: Progress thread is started.")
                synchronized(this) {
                    while (mProgressStatus) {
                        Log.d(TAG, "onFingerChange: mProgressStatus is true")
                        runOnUiThread {
                            viewBreathInIVSelectHome.visibility = View.GONE
                            viewBreathOutIVSelectHome.visibility = View.GONE
                            viewGrayBreathInHome.visibility = View.VISIBLE
                            viewGrayBreathOutHome.visibility = View.VISIBLE
                        }

                        if (progress == MIN_PROGRESS) {
                            runOnUiThread {
                                viewGrayBreathInHome.visibility = View.GONE
                                viewBreathInIVSelectHome.visibility = View.VISIBLE
                            }
                            while (progress < MAX_PROGRESS) {
                                if (!mProgressStatus)
                                    break
                                runOnUiThread { progressBarHome.progress = progress++ }
                                Thread.sleep(1000)
                                Log.d(TAG, "onFingerChange: Increase progress: $progress")
                            }
                            runOnUiThread {
                                viewGrayBreathInHome.visibility = View.VISIBLE
                                viewBreathInIVSelectHome.visibility = View.GONE
                            }
                        }

                        if (progress == MAX_PROGRESS) {
                            runOnUiThread {
                                viewGrayBreathOutHome.visibility = View.GONE
                                viewBreathOutIVSelectHome.visibility = View.VISIBLE
                            }
                            while (progress > MIN_PROGRESS) {
                                if (!mProgressStatus)
                                    break
                                runOnUiThread { progressBarHome.progress = progress-- }
                                Thread.sleep(1000)
                                Log.d(TAG, "onFingerChange: Decrease progress: $progress")
                            }
                            runOnUiThread {
                                viewGrayBreathOutHome.visibility = View.VISIBLE
                                viewBreathOutIVSelectHome.visibility = View.GONE
                            }
                        }
                    }
                    mProgressStatus = false
                }
            }.start()
        }
    }
}