package com.puzzlescan.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final int PICK_REFERENCE = 5001;
    private JavaCameraView cameraView;
    private ScanOverlayView overlay;
    private TargetImageView referenceView;
    private TextView status,title,confidence,method,rotation,coords;
    private Button pieceBtn,fragmentBtn;

    // Важно: PieceMatcher внутри создаёт SIFT, который использует native OpenCV.
    // Поэтому его нельзя создавать как поле Activity до OpenCVLoader.initLocal().
    private PieceMatcher matcher;

    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private volatile boolean fragmentMode=false;
    private volatile boolean referenceReady=false;
    private long lastScan=0;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);

        // Native-библиотеки OpenCV должны быть загружены ДО создания PieceMatcher/SIFT.
        if(!OpenCVLoader.initLocal()){
            Toast.makeText(this,"OpenCV не загрузился",Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            matcher = new PieceMatcher();
        } catch (Throwable t) {
            Toast.makeText(this,"Не удалось запустить модуль распознавания: "+t.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        cameraView=findViewById(R.id.camera_view);overlay=findViewById(R.id.scan_overlay);referenceView=findViewById(R.id.reference_view);
        status=findViewById(R.id.status_chip);title=findViewById(R.id.match_title);confidence=findViewById(R.id.match_confidence);
        method=findViewById(R.id.match_method);rotation=findViewById(R.id.match_rotation);coords=findViewById(R.id.match_coords);
        pieceBtn=findViewById(R.id.btn_piece);fragmentBtn=findViewById(R.id.btn_fragment);Button refBtn=findViewById(R.id.btn_reference);
        cameraView.setVisibility(SurfaceView.VISIBLE);cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
        cameraView.setMaxFrameSize(1280,960);cameraView.setCvCameraViewListener(this);
        pieceBtn.setOnClickListener(v->setMode(false));fragmentBtn.setOnClickListener(v->setMode(true));refBtn.setOnClickListener(v->pickReference());
        setMode(false);status.setText("Сначала нажмите «Эталон» и выберите исходное фото");
    }

    private void setMode(boolean fragment){
        fragmentMode=fragment;pieceBtn.setText(fragment?"Скан детали":"✓ Скан детали");fragmentBtn.setText(fragment?"✓ Скан фрагмента":"Скан фрагмента");
        referenceView.clearTarget();overlay.setState(Color.WHITE,fragment?"Фрагмент целиком внутри рамки":"Одна деталь целиком внутри рамки");
        if(referenceReady)status.setText(fragment?"Покажите собранный фрагмент":"Покажите одну деталь");
    }

    private void pickReference(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,PICK_REFERENCE);}

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);if(req!=PICK_REFERENCE||result!=Activity.RESULT_OK||data==null||data.getData()==null)return;
        Uri u=data.getData();try(InputStream in=getContentResolver().openInputStream(u)){Bitmap bmp=BitmapFactory.decodeStream(in);if(bmp==null)throw new Exception("decode");
            referenceView.setReferenceBitmap(bmp);status.setText("Подготавливаю эталон…");referenceReady=false;Mat m=new Mat();Utils.bitmapToMat(bmp,m);
            worker.execute(()->{try{matcher.setReference(m);referenceReady=true;runOnUiThread(()->status.setText(fragmentMode?"Покажите собранный фрагмент":"Покажите одну деталь"));}
                catch(Throwable t){referenceReady=false;runOnUiThread(()->{status.setText("Ошибка подготовки эталона");Toast.makeText(this,"Ошибка OpenCV: "+t.getClass().getSimpleName(),Toast.LENGTH_LONG).show();});}
                finally{m.release();}});
        }catch(Exception e){Toast.makeText(this,"Не удалось открыть изображение",Toast.LENGTH_LONG).show();}
    }

    @Override public void onCameraViewStarted(int w,int h){}
    @Override public void onCameraViewStopped(){}

    @Override public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame f){
        Mat rgba=f.rgba();long now=SystemClock.elapsedRealtime();long interval=fragmentMode?1900:1350;
        if(referenceReady&&matcher!=null&&now-lastScan>interval&&busy.compareAndSet(false,true)){
            lastScan=now;Mat snap=rgba.clone();boolean mode=fragmentMode;
            runOnUiThread(()->{status.setText("Сканирую…");overlay.setState(Color.rgb(108,69,200),"Выделяю реальный контур…");});
            worker.execute(()->{MatchResult r;try{r=matcher.analyze(snap,mode);}catch(Throwable t){r=MatchResult.noMatch("Ошибка анализа");}finally{snap.release();}
                MatchResult rr=r;runOnUiThread(()->show(rr));busy.set(false);});
        }return rgba;
    }

    private void show(MatchResult r){
        if(!r.pieceDetected){status.setText("Деталь не выделена");title.setText("Ищу деталь");confidence.setText("Точность: —");method.setText("Метод: произвольный контур");rotation.setText("Поворот: —");coords.setText("Место: —");referenceView.clearTarget();overlay.setState(Color.WHITE,"Деталь целиком внутри рамки");return;}
        if(!r.found){status.setText("Однозначного места нет");title.setText("Нужно больше контекста");confidence.setText("Точность: недостаточно");method.setText("Совет: режим фрагмента");rotation.setText("Поворот: —");coords.setText("Место: —");referenceView.clearTarget();overlay.setState(Color.rgb(242,169,0),"Покажите соседний фрагмент");return;}
        int pct=(int)Math.round(r.confidence*100);title.setText(r.ambiguous?"Лучший вариант":"Совпадение найдено");confidence.setText("Точность: "+pct+"%");method.setText("Метод: "+r.method);
        rotation.setText(String.format(Locale.US,"Поворот: %.0f°",r.rotationDeg));coords.setText(String.format(Locale.US,"Место: %.1f%% × %.1f%%",r.centerXPercent,r.centerYPercent));referenceView.showTarget(r.polygon,r.ambiguous);
        if(r.ambiguous){status.setText("Есть похожие места — показан лучший");overlay.setState(Color.rgb(242,169,0),"Вероятное совпадение");}
        else{status.setText("Место найдено — подсвечено ниже");overlay.setState(Color.rgb(54,182,107),"Совпадение найдено");}
    }

    @Override protected List<? extends CameraBridgeViewBase> getCameraViewList(){return Collections.singletonList(cameraView);}
    @Override protected void onResume(){super.onResume();if(cameraView!=null)cameraView.enableView();}
    @Override protected void onPause(){if(cameraView!=null)cameraView.disableView();super.onPause();}
    @Override protected void onDestroy(){if(cameraView!=null)cameraView.disableView();worker.shutdownNow();super.onDestroy();}
}
