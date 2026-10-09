package com.softbankrobotics.pepper.pepperGPT.validation;
import android.app.*;import android.os.Bundle;import android.content.Intent;import java.lang.reflect.Method;import java.io.File;
public class FeatureProbe extends Instrumentation {
 private boolean story; private final Bundle result = new Bundle();
 public void onCreate(Bundle args) { super.onCreate(args); story=args!=null&&"true".equals(args.getString("story")); start(); }
 private void report(String message) {Bundle status=new Bundle();status.putString("stream",message+"\n");sendStatus(0,status);}
 private Object call(Activity activity,String name, String text) throws Exception {Method m=activity.getClass().getDeclaredMethod(name,String.class);m.setAccessible(true);return m.invoke(activity,text);}
 public void onStart() { try {
 Intent intent=new Intent();intent.setClassName(getTargetContext(),"com.softbankrobotics.pepper.pepperGPT.MainActivity");intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
 final Activity main=startActivitySync(intent);waitForIdleSync();
 String[][] cases={
 {"isStoryRequest","Eh, mi racconti una piccola storia?"},{"isStoryRequest","No, tu raccontamela."},{"isStoryRequest","Una storia, per favore."},{"isStoryRequest","Vorrei una piccola favola"},{"isStoryRequest","Mi leggi una bella fiaba?"},{"isStoryRequest","Tell me a story about Mars"},
 {"isRecipeRequest","Mi suggerisci una buona ricetta per la pasta?"},{"isRecipeRequest","Come si prepara il risotto?"},{"isRecipeRequest","Quali sono gli ingredienti della carbonara?"},{"isRecipeRequest","Recipe for pasta"},
 {"isRadioRequest","Mi metti un po' di musica pop?"},{"isRadioRequest","Puoi accendere la radio pop?"},{"isRadioRequest","Vorrei ascoltare un po' di synthwave"},{"isRadioRequest","Puoi fermare la musica?"},{"isRadioRequest","Play pop music"},
 {"isImageRequest","Puoi creare una bella immagine di un gatto?"},{"isImageRequest","Vorrei un piccolo disegno di Pepper"},{"isImageRequest","Disegnami un robot"},{"isImageRequest","Generate an image of a robot"},
 {"isTransformRequest","Mi fai una bella foto?"},{"isTransformRequest","Puoi scattarmi una fotografia?"},{"isTransformRequest","Puoi immaginarmi come un astronauta?"},{"isTransformRequest","Come sarei se fossi un pirata?"},{"isTransformRequest","Take a photo"}};
 for(String[] test:cases) if(!Boolean.TRUE.equals(call(main,test[0],test[1])))throw new AssertionError(test[0]+" missed: "+test[1]);
 String[] negatives={"Vi racconto una piccola storia","Ho letto una ricetta interessante","Ho ascoltato la radio ieri","Ho visto una bella immagine","Ho scattato una foto"};
 String[] predicates={"isStoryRequest","isRecipeRequest","isRadioRequest","isImageRequest","isTransformRequest"};
 for(String text:negatives)for(String method:predicates)if(Boolean.TRUE.equals(call(main,method,text)))throw new AssertionError("Unexpected "+method+": "+text);
 String[][] weather={{"Mi dici le previsioni del tempo per Milano oggi?","Milano","weather"},{"Com\u2019\u00e8 il tempo a Londra, per favore?","London","weather"},{"Meteo Roma","Roma","weather"},{"Che tempo fa oggi?","","weather"},{"Che temperatura c'\u00e8 a Milano?","Milano","temperature"},{"Quanti gradi ci sono?","","temperature"},{"Che ore sono a Tokyo?","Tokyo","time"},{"Che ora \u00e8?","","time"},{"Ho visto le previsioni del tempo","","none"},{"Weather in London","London","weather"}};
 for(String[] test:weather){Object pair=call(main,"extractCityAndRequestType",test[0]);Object city=pair.getClass().getMethod("getFirst").invoke(pair);Object mode=pair.getClass().getMethod("getSecond").invoke(pair);if(!test[1].equals(city)||!test[2].equals(mode))throw new AssertionError("Location routing failed: "+test[0]+" -> "+city+"/"+mode);}
 report("PASS: 24 real feature predicates, 25 negative checks, 10 weather/time/city checks on tablet.");
 if(story){ActivityMonitor monitor=addMonitor("com.softbankrobotics.pepper.pepperGPT.ImmersiveModeActivity",null,false);
 runOnMainSync(()->{try{call(main,"sendToOpenAI","Eh, mi racconti una piccola storia sui robot?");}catch(Exception ex){throw new RuntimeException(ex);}});
 report("Submitted exact Italian story request through original MainActivity dispatcher.");
 Activity screen=waitForMonitorWithTimeout(monitor,150000);if(screen==null)throw new AssertionError("Story screen did not open within 150 seconds");
 waitForIdleSync();Intent data=screen.getIntent();if(!"story".equals(data.getStringExtra("EXTRA_MODE")))throw new AssertionError("Wrong immersive mode");String content=data.getStringExtra("EXTRA_CONTENT");if(content==null||content.isEmpty())throw new AssertionError("Missing story content");
 String image=data.getStringExtra("EXTRA_IMAGE_PATH");if(image==null||!new File(image).isFile())throw new AssertionError("Missing first illustrated scene file");
 android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(image,options);if(options.outWidth<=0||options.outHeight<=0)throw new AssertionError("First image is invalid");
 String sceneText=data.getStringExtra("EXTRA_SCENE_TEXTS");org.json.JSONArray scenes=new org.json.JSONArray(sceneText==null?"[]":sceneText);if(scenes.length()<1||scenes.length()>3)throw new AssertionError("Invalid original scene count: "+scenes.length());
 org.json.JSONObject evidence=new org.json.JSONObject();evidence.put("content",content);evidence.put("scenes",scenes.length());evidence.put("imageWidth",options.outWidth);evidence.put("imageHeight",options.outHeight);try(java.io.FileOutputStream file=new java.io.FileOutputStream(new File(getTargetContext().getFilesDir(),"feature-probe-result.json"))){file.write(evidence.toString().getBytes("UTF-8"));}
 report("PASS: original story dispatcher opened illustrated Story Mode with complete narrative scenes and a valid cached image.");Thread.sleep(6000);
 }
 result.putString("stream","PASS\n");finish(Activity.RESULT_OK,result);
 } catch(Throwable error){result.putString("stream","FAIL: "+error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage())+"\n");finish(Activity.RESULT_CANCELED,result);}
 }
}