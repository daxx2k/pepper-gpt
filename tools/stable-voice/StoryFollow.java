package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Story-only UI, driven by playback boundaries rather than a reading-speed timer. */
public final class StoryFollow {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, State> STATES = new WeakHashMap<>();
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern SENTENCE = Pattern.compile("[.!?][\\\"'”’)]*(?:\\s+|$)");
    private StoryFollow() {}

    public static boolean story(Activity activity) {
        return activity != null && "story".equals(activity.getIntent().getStringExtra("EXTRA_MODE"))
                && "ImmersiveModeActivity".equals(activity.getClass().getSimpleName());
    }
    private static Object field(Activity a,String name) throws Exception {
        Field f=a.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(a);
    }
    private static State state(Activity a) {
        State st=STATES.get(a);
        if(st==null){st=new State();STATES.put(a,st);}
        return st;
    }
    private static final class State {
        int scene, cursor;
        boolean illustrationReady, tabletCuePlayed, narrationStarted;
        Runnable waitingPose;
        View loadingView;
        android.view.ViewTreeObserver.OnGlobalLayoutListener loadingListener;
        boolean waitingPoseUsed, waitingPoseActive;
        long illustrationRequestedAt;
    }
    public static void attach(Activity a) {
        try {
            View loading = (View)field(a, "loading");
            if (!story(a)) {
                State st = state(a);
                st.loadingView = loading;
                st.loadingListener = () -> {
                    if (loading.isShown() && loading.getAlpha() > 0) RealtimeLab.spinnerShown(a);
                    else RealtimeLab.spinnerHidden(a);
                };
                loading.getViewTreeObserver().addOnGlobalLayoutListener(st.loadingListener);
                return;
            }
            ((View)field(a, "textView")).setVisibility(View.INVISIBLE);
            ((View)field(a, "placeholder")).setAlpha(0f);
            ((View)field(a, "loading")).setAlpha(0f);
        } catch (Exception ignored) {}
    }
    public static void detach(Activity a) {
        stopWaiting(a);
        RealtimeLab.spinnerHidden(a);
        ContextGesture.stop(a);
        State st = STATES.remove(a);
        if (st != null && st.loadingListener != null && st.loadingView.getViewTreeObserver().isAlive())
            st.loadingView.getViewTreeObserver().removeOnGlobalLayoutListener(st.loadingListener);
    }

    public static void illustrationRequested(Activity a, int index) {
        if (index != 0 || !story(a)) return;
        State st = state(a);
        if (st.waitingPose != null || st.illustrationReady || st.narrationStarted) return;
        st.illustrationRequestedAt = android.os.SystemClock.elapsedRealtime();
        st.waitingPose = () -> {
            if (!st.narrationStarted) playWaitingPose(a);
        };
        UI.postDelayed(st.waitingPose, 2000);
    }

    static boolean playWaitingPose(Activity a) {
        State st = STATES.get(a);
        if (st == null || st.waitingPose == null || st.illustrationReady) return false;
        if (st.waitingPoseActive) return true;
        if (st.waitingPoseUsed || android.os.SystemClock.elapsedRealtime() - st.illustrationRequestedAt < 2000)
            return false;
        st.waitingPoseActive = ContextGesture.play(a, LegacyVoiceAdapter.focusedContext(a),
                "illustration_wait", () -> st.waitingPoseActive = false);
        st.waitingPoseUsed = st.waitingPoseActive;
        return st.waitingPoseActive;
    }

    public static void stopWaiting(Activity a) {
        State st = STATES.get(a);
        if (st == null || st.waitingPose == null) return;
        UI.removeCallbacks(st.waitingPose);
        st.waitingPose = null;
        st.waitingPoseActive = false;
        ContextGesture.stop(a, "illustration_wait");
    }
    public static void pending(Activity a) {
        if (!story(a)) return;
        try {
            android.widget.ImageView image = (android.widget.ImageView)field(a, "imageView");
            image.animate().cancel();
            if (image.getDrawable() == null) image.setVisibility(View.INVISIBLE);
            else image.setAlpha(1f);
        } catch (Exception ignored) {}
    }
    public static void ready(Activity a) {
        if (a.isFinishing() || a.isDestroyed()) return;
        String mode = a.getIntent().getStringExtra("EXTRA_MODE");
        if (!"story".equals(mode) && !"recipe".equals(mode)) return;
        try {
            View image = (View)field(a, "imageView");
            if (image.getVisibility() != View.VISIBLE) return;
            if ("story".equals(mode)) {
                image.animate().cancel();
                image.setAlpha(0f);
                image.animate().alpha(1f).setDuration(750).start();
            }
            stopWaiting(a);
            state(a).illustrationReady = true;
            tryTabletCue(a);
        } catch (Exception ignored) {}
    }
    static void tryTabletCue(Activity a) {
        State st = STATES.get(a);
        if (st == null || !st.illustrationReady || st.tabletCuePlayed
                || (story(a) && !st.narrationStarted)) return;
        st.tabletCuePlayed = ContextGesture.play(a, LegacyVoiceAdapter.focusedContext(a), "show_tablet_01", null);
    }

    public static void scene(Activity a,int index) {
        if(!story(a))return;
        State st=state(a); st.scene=index;
    }
    public static void heard(Activity a,String chunk) {
        if(!story(a))return;
        UI.post(() -> {
            if(a.isFinishing() || a.isDestroyed())return;
            try {
                if (!Boolean.TRUE.equals(field(a,"speechRunning"))) return;
                TextView text=(TextView)field(a,"textView");
                if (text.getVisibility() != View.VISIBLE) text.setVisibility(View.VISIBLE);
                state(a).narrationStarted = true;
                tryTabletCue(a);
                if (Boolean.TRUE.equals(field(a,"userScrollActive"))) return;
                ScrollView scroll=(ScrollView)field(a,"scrollView");
                Layout layout=text.getLayout();if(layout==null)return;
                State st=state(a);
                int found=findAnchor(text.getText().toString(),chunk,st.cursor);
                if(found<0) {
                    // A regenerated speech variant may differ from the printed text. Keep
                    // the current location rather than jumping to an unrelated paragraph.
                    return;
                }
                st.cursor=found+1;
                int line=layout.getLineForOffset(found);
                int y=text.getTop()+text.getTotalPaddingTop()+layout.getLineTop(line)-scroll.getHeight()/7;
                scroll.smoothScrollTo(0,Math.max(0,y));
                Log.i("PepperStory","Audio-follow anchor, scene "+(st.scene+1)+", line "+line);
            } catch(Exception e) { Log.w("PepperStory","Could not follow the narration"); }
        });
    }
    static int findAnchor(String display,String speech,int minimum) {
        String clean=speech.replaceAll("\\\\(?:rspd|pau|vol|vct|pitch|rate|mrk)=[^\\\\]*\\\\"," ");
        List<String> needle=new ArrayList<>();
        Matcher part=WORD.matcher(clean);
        while(part.find() && needle.size()<7)needle.add(part.group().toLowerCase(Locale.ROOT));
        if(needle.isEmpty())return -1;
        List<String> words=new ArrayList<>();List<Integer> at=new ArrayList<>();
        Matcher full=WORD.matcher(display);
        while(full.find())if(full.start()>=minimum){words.add(full.group().toLowerCase(Locale.ROOT));at.add(full.start());}
        for(int i=0;i+needle.size()<=words.size();i++)
            if(words.subList(i,i+needle.size()).equals(needle))return at.get(i);
        return -1;
    }
    static List<String> segments(String text) {
        List<String> result=new ArrayList<>();
        StringBuilder pending=new StringBuilder();
        Matcher matcher=SENTENCE.matcher(text);
        int start=0;
        while(matcher.find()){
            pack(result,pending,text.substring(start,matcher.end()).trim()); start=matcher.end();
        }
        pack(result,pending,text.substring(start).trim());
        if(pending.length()>0)result.add(pending.toString());
        return result;
    }
    private static void pack(List<String> result,StringBuilder pending,String sentence) {
        if(sentence.isEmpty())return;
        if(pending.length()>0 && pending.length()+1+sentence.length()>420){
            result.add(pending.toString());pending.setLength(0);
        }
        if(sentence.length()>520){
            result.addAll(SpeechChunks.split(sentence,420,420,false));return;
        }
        if(pending.length()>0)pending.append(' ');
        pending.append(sentence);
    }
}
