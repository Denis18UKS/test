package com.puzzlescan.app;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.SIFT;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class PieceMatcher {
    private Mat refFull, refWork, refDesc;
    private MatOfKeyPoint refKp;
    private double workToFull = 1.0;
    private final SIFT sift = SIFT.create();

    public synchronized void setReference(Mat rgba) {
        releaseRef();
        refFull = new Mat();
        Imgproc.cvtColor(rgba, refFull, Imgproc.COLOR_RGBA2BGR);
        int w = Math.min(640, refFull.cols());
        double s = (double) w / refFull.cols();
        refWork = new Mat();
        Imgproc.resize(refFull, refWork, new Size(w, Math.round(refFull.rows() * s)), 0, 0, Imgproc.INTER_AREA);
        workToFull = (double) refFull.cols() / refWork.cols();
        Mat gray = new Mat();
        Imgproc.cvtColor(refWork, gray, Imgproc.COLOR_BGR2GRAY);
        refKp = new MatOfKeyPoint();
        refDesc = new Mat();
        sift.detectAndCompute(gray, new Mat(), refKp, refDesc);
        gray.release();
    }

    public synchronized MatchResult analyze(Mat frameRgba, boolean fragmentMode) {
        if (refFull == null || refFull.empty()) return MatchResult.noMatch("Сначала загрузите эталон");
        Seg seg = segment(frameRgba, fragmentMode);
        if (seg == null) return MatchResult.noPiece("Поместите деталь целиком внутрь рамки");
        MatchResult r = matchSift(seg);
        if (r == null || !r.found || r.confidence < 0.68) r = matchTemplate(seg, fragmentMode);
        seg.release();
        return r != null ? r : MatchResult.noMatch("Не удалось определить место однозначно. Попробуйте режим фрагмента.");
    }

    private Seg segment(Mat rgba, boolean fragmentMode) {
        int fw = rgba.cols(), fh = rgba.rows();
        Rect roiRect = new Rect((int)(fw*.14), (int)(fh*.13), (int)(fw*.72), (int)(fh*.68));
        roiRect.width = Math.min(roiRect.width, fw - roiRect.x);
        roiRect.height = Math.min(roiRect.height, fh - roiRect.y);
        Mat crop = new Mat(rgba, roiRect).clone();
        Mat bgr = new Mat();
        Imgproc.cvtColor(crop, bgr, Imgproc.COLOR_RGBA2BGR);
        crop.release();
        if (bgr.cols() > 640) {
            double s = 640.0 / bgr.cols();
            Mat t = new Mat();
            Imgproc.resize(bgr, t, new Size(640, Math.round(bgr.rows()*s)), 0, 0, Imgproc.INTER_AREA);
            bgr.release(); bgr = t;
        }

        Mat gc = new Mat(bgr.size(), CvType.CV_8UC1, new Scalar(Imgproc.GC_BGD));
        Mat bg = new Mat(), fg = new Mat();
        int mx = Math.max(8, bgr.cols()/18), my = Math.max(8, bgr.rows()/18);
        try {
            Imgproc.grabCut(bgr, gc, new Rect(mx,my,bgr.cols()-mx*2,bgr.rows()-my*2), bg, fg,
                    fragmentMode ? 3 : 2, Imgproc.GC_INIT_WITH_RECT);
        } catch (Exception e) {
            bgr.release(); gc.release(); bg.release(); fg.release(); return null;
        }
        bg.release(); fg.release();
        Mat a = new Mat(), p = new Mat(), mask = new Mat();
        Core.inRange(gc, new Scalar(Imgproc.GC_FGD), new Scalar(Imgproc.GC_FGD), a);
        Core.inRange(gc, new Scalar(Imgproc.GC_PR_FGD), new Scalar(Imgproc.GC_PR_FGD), p);
        Core.bitwise_or(a,p,mask); a.release(); p.release(); gc.release();
        Mat k = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5,5));
        Imgproc.morphologyEx(mask,mask,Imgproc.MORPH_OPEN,k);
        Imgproc.morphologyEx(mask,mask,Imgproc.MORPH_CLOSE,k); k.release();

        List<MatOfPoint> cs = new ArrayList<>();
        Mat h = new Mat(), tmp = mask.clone();
        Imgproc.findContours(tmp,cs,h,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
        tmp.release(); h.release();
        MatOfPoint best = null; double bestScore=0, total=bgr.cols()*(double)bgr.rows();
        Point center = new Point(bgr.cols()/2.0,bgr.rows()/2.0);
        for (MatOfPoint c:cs) {
            double area=Imgproc.contourArea(c); if(area<total*(fragmentMode?.006:.0025)) continue;
            Rect r=Imgproc.boundingRect(c); double d=Math.hypot(r.x+r.width/2.0-center.x,r.y+r.height/2.0-center.y);
            double score=area/(1+d*.02); if(score>bestScore){bestScore=score;best=c;}
        }
        if(best==null){bgr.release();mask.release();for(MatOfPoint c:cs)c.release();return null;}
        Rect r=Imgproc.boundingRect(best); int pad=Math.max(3,Math.min(r.width,r.height)/20);
        r.x=Math.max(0,r.x-pad);r.y=Math.max(0,r.y-pad);
        r.width=Math.min(bgr.cols()-r.x,r.width+pad*2);r.height=Math.min(bgr.rows()-r.y,r.height+pad*2);
        Mat piece=new Mat(bgr,r).clone(), pmask=new Mat(mask,r).clone();
        Imgproc.threshold(pmask,pmask,127,255,Imgproc.THRESH_BINARY);
        Point[] poly=approx(best,r);
        bgr.release();mask.release();for(MatOfPoint c:cs)c.release();
        return new Seg(piece,pmask,poly);
    }

    private Point[] approx(MatOfPoint c, Rect r) {
        MatOfPoint2f p=new MatOfPoint2f(c.toArray()), out=new MatOfPoint2f();
        Imgproc.approxPolyDP(p,out,Math.max(1.5,Imgproc.arcLength(p,true)*.015),true);
        Point[] pts=out.toArray(); p.release(); out.release();
        if(pts.length<3||pts.length>32) return rectPoints(r.width,r.height);
        for(Point q:pts){q.x-=r.x;q.y-=r.y;} return pts;
    }

    private MatchResult matchSift(Seg s) {
        Mat gray=new Mat();Imgproc.cvtColor(s.image,gray,Imgproc.COLOR_BGR2GRAY);
        MatOfKeyPoint kp=new MatOfKeyPoint();Mat desc=new Mat();sift.detectAndCompute(gray,s.mask,kp,desc);gray.release();
        if(desc.empty()||refDesc==null||refDesc.empty()){kp.release();desc.release();return null;}
        List<MatOfDMatch> knn=new ArrayList<>();BFMatcher.create(Core.NORM_L2,false).knnMatch(desc,refDesc,knn,2);
        List<DMatch> good=new ArrayList<>();
        for(MatOfDMatch m:knn){DMatch[] d=m.toArray();if(d.length>1&&d[0].distance<.74*d[1].distance)good.add(d[0]);m.release();}
        MatchResult out=null;
        if(good.size()>=4){
            KeyPoint[] a=kp.toArray(),b=refKp.toArray();Point[] x=new Point[good.size()],y=new Point[good.size()];
            for(int i=0;i<good.size();i++){DMatch d=good.get(i);x[i]=a[d.queryIdx].pt;y[i]=b[d.trainIdx].pt;}
            MatOfPoint2f src=new MatOfPoint2f(x),dst=new MatOfPoint2f(y);Mat in=new Mat();
            Mat H=Calib3d.findHomography(src,dst,Calib3d.RANSAC,4,in);int n=in.empty()?0:Core.countNonZero(in);
            if(!H.empty()&&n>=4){
                MatOfPoint2f pp=new MatOfPoint2f(s.poly),qq=new MatOfPoint2f();Core.perspectiveTransform(pp,qq,H);
                Point[] q=qq.toArray(); if(reasonable(q)){
                    double ratio=n/(double)good.size();out=new MatchResult();out.pieceDetected=true;out.found=true;
                    out.confidence=Math.min(.99,.50+ratio*.30+Math.min(n,16)/16.0*.18);out.ambiguous=out.confidence<.78;
                    out.method="SIFT + RANSAC";out.rotationDeg=rotation(H);out.polygon=toFloat(q,workToFull);center(out);
                } pp.release();qq.release();
            }
            src.release();dst.release();in.release();H.release();
        }
        kp.release();desc.release();return out;
    }

    private MatchResult matchTemplate(Seg s, boolean fragmentMode) {
        double[] sizes=fragmentMode?new double[]{.10,.16,.24,.34,.48}:new double[]{.025,.035,.05,.07,.095,.13};
        Candidate best=null,second=null; int longest=Math.max(refWork.cols(),refWork.rows());
        for(int angle=0;angle<360;angle+=45){
            Pair rot=rotate(s.image,s.mask,angle);
            for(double pct:sizes){
                double sc=(longest*pct)/Math.max(rot.image.cols(),rot.image.rows());
                int w=Math.max(8,(int)Math.round(rot.image.cols()*sc)),hh=Math.max(8,(int)Math.round(rot.image.rows()*sc));
                if(w>=refWork.cols()||hh>=refWork.rows())continue;
                Mat im=new Mat(),ma=new Mat();Imgproc.resize(rot.image,im,new Size(w,hh));Imgproc.resize(rot.mask,ma,new Size(w,hh),0,0,Imgproc.INTER_NEAREST);
                Imgproc.threshold(ma,ma,127,255,Imgproc.THRESH_BINARY);Mat res=new Mat();
                try{Imgproc.matchTemplate(refWork,im,res,Imgproc.TM_CCORR_NORMED,ma);Core.MinMaxLocResult mm=Core.minMaxLoc(res);
                    if(Double.isFinite(mm.maxVal)){Candidate c=new Candidate(mm.maxVal,mm.maxLoc,angle,ma.clone(),w,hh);
                        if(best==null||c.score>best.score){if(second!=null)second.release();second=best;best=c;}
                        else if(second==null||c.score>second.score){if(second!=null)second.release();second=c;}else c.release();}}
                catch(Exception ignored){} res.release();im.release();ma.release();
            } rot.release(); if(best!=null&&best.score>.975)break;
        }
        if(best==null||best.score<(fragmentMode?.72:.76)){if(best!=null)best.release();if(second!=null)second.release();return null;}
        double gap=second==null?1:best.score-second.score;Point[] p=maskPoly(best.mask);if(p.length<3)p=rectPoints(best.w,best.h);
        for(Point q:p){q.x=(q.x+best.loc.x)*workToFull;q.y=(q.y+best.loc.y)*workToFull;}
        MatchResult r=new MatchResult();r.pieceDetected=true;r.found=true;r.method="Контур + шаблон";r.rotationDeg=best.angle;
        r.confidence=Math.max(0,Math.min(.96,(best.score-.45)/.55));r.ambiguous=best.score<.86||gap<.018;r.polygon=toFloat(p,1);center(r);
        best.release();if(second!=null)second.release();return r;
    }

    private Pair rotate(Mat im,Mat mask,double a){if(a==0)return new Pair(im.clone(),mask.clone());Point c=new Point(im.cols()/2.0,im.rows()/2.0);
        Mat m=Imgproc.getRotationMatrix2D(c,a,1);double co=Math.abs(m.get(0,0)[0]),si=Math.abs(m.get(0,1)[0]);int w=(int)(im.rows()*si+im.cols()*co),h=(int)(im.rows()*co+im.cols()*si);
        m.put(0,2,m.get(0,2)[0]+w/2.0-c.x);m.put(1,2,m.get(1,2)[0]+h/2.0-c.y);Mat x=new Mat(),y=new Mat();
        Imgproc.warpAffine(im,x,m,new Size(w,h),Imgproc.INTER_LINEAR,Core.BORDER_CONSTANT,new Scalar(0,0,0));Imgproc.warpAffine(mask,y,m,new Size(w,h),Imgproc.INTER_NEAREST,Core.BORDER_CONSTANT,new Scalar(0));m.release();return new Pair(x,y);}

    private Point[] maskPoly(Mat mask){List<MatOfPoint> cs=new ArrayList<>();Mat h=new Mat(),t=mask.clone();Imgproc.findContours(t,cs,h,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);t.release();h.release();
        MatOfPoint best=null;double area=-1;for(MatOfPoint c:cs){double a=Imgproc.contourArea(c);if(a>area){area=a;best=c;}}if(best==null)return new Point[0];
        MatOfPoint2f p=new MatOfPoint2f(best.toArray()),q=new MatOfPoint2f();Imgproc.approxPolyDP(p,q,Math.max(1,Imgproc.arcLength(p,true)*.02),true);Point[] out=q.toArray();p.release();q.release();for(MatOfPoint c:cs)c.release();return out;}

    private boolean reasonable(Point[] p){if(p.length<3)return false;int in=0;for(Point q:p)if(q.x>-refWork.cols()*.1&&q.x<refWork.cols()*1.1&&q.y>-refWork.rows()*.1&&q.y<refWork.rows()*1.1)in++;return in>=Math.max(3,p.length*2/3);}
    private double rotation(Mat H){double d=Math.toDegrees(Math.atan2(H.get(1,0)[0],H.get(0,0)[0]));return d<0?d+360:d;}
    private float[] toFloat(Point[] p,double s){int n=Math.min(p.length,32);float[] a=new float[n*2];for(int i=0;i<n;i++){a[i*2]=(float)(p[i].x*s);a[i*2+1]=(float)(p[i].y*s);}return a;}
    private Point[] rectPoints(int w,int h){return new Point[]{new Point(0,0),new Point(w,0),new Point(w,h),new Point(0,h)};}
    private void center(MatchResult r){double x=0,y=0;int n=r.polygon.length/2;for(int i=0;i<r.polygon.length;i+=2){x+=r.polygon[i];y+=r.polygon[i+1];}r.centerXPercent=x/n/refFull.cols()*100;r.centerYPercent=y/n/refFull.rows()*100;}
    private void releaseRef(){if(refFull!=null)refFull.release();if(refWork!=null)refWork.release();if(refDesc!=null)refDesc.release();if(refKp!=null)refKp.release();refFull=refWork=refDesc=null;refKp=null;}

    private static class Seg{final Mat image,mask;final Point[] poly;Seg(Mat i,Mat m,Point[] p){image=i;mask=m;poly=p;}void release(){image.release();mask.release();}}
    private static class Pair{final Mat image,mask;Pair(Mat i,Mat m){image=i;mask=m;}void release(){image.release();mask.release();}}
    private static class Candidate{final double score;final Point loc;final int angle,w,h;final Mat mask;Candidate(double s,Point l,int a,Mat m,int w,int h){score=s;loc=l;angle=a;mask=m;this.w=w;this.h=h;}void release(){mask.release();}}
}
