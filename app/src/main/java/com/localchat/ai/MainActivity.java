package com.localchat.ai;
import android.app.*; import android.os.*; import android.graphics.Color; import android.view.*; import android.webkit.*; import android.widget.*;
public class MainActivity extends Activity {
 static final String P="localchat",K="server"; WebView w;
 String norm(String s){s=s==null?"":s.trim();if(s.isEmpty())return "";if(!s.startsWith("http://")&&!s.startsWith("https://"))s="http://"+s;while(s.endsWith("/"))s=s.substring(0,s.length()-1);return s;}
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);String u=norm(getSharedPreferences(P,0).getString(K,""));if(u.isEmpty())setup();else browser(u);}
 void setup(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(40,80,40,40);l.setGravity(Gravity.CENTER_HORIZONTAL);l.setBackgroundColor(Color.BLACK);
 TextView t=new TextView(this);t.setText("LocalChat AI");t.setTextSize(30);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER);l.addView(t,new LinearLayout.LayoutParams(-1,-2));
 TextView p=new TextView(this);p.setText("\nDeine lokale KI auf dem PC\n\nGib die Adresse ein, die dein LocalChat-Server anzeigt.\nPC und Xiaomi müssen im selben WLAN sein.\n");p.setTextColor(Color.LTGRAY);p.setGravity(Gravity.CENTER);l.addView(p,new LinearLayout.LayoutParams(-1,-2));
 EditText e=new EditText(this);e.setSingleLine();e.setHint("192.168.1.42:8787");e.setTextColor(Color.WHITE);e.setHintTextColor(Color.GRAY);l.addView(e,new LinearLayout.LayoutParams(-1,-2));
 Button c=new Button(this);c.setText("Verbinden");l.addView(c,new LinearLayout.LayoutParams(-1,-2));c.setOnClickListener(v->{String u=norm(e.getText().toString());if(u.isEmpty())return;getSharedPreferences(P,0).edit().putString(K,u).apply();browser(u);});setContentView(l);}
 void browser(String u){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setBackgroundColor(Color.BLACK);LinearLayout bar=new LinearLayout(this);
 Button b=new Button(this);b.setText("‹");Button r=new Button(this);r.setText("↻");Button m=new Button(this);m.setText("⋮");TextView t=new TextView(this);t.setText("LocalChat AI");t.setTextColor(Color.WHITE);t.setTextSize(18);t.setGravity(Gravity.CENTER_VERTICAL);
 bar.addView(b,new LinearLayout.LayoutParams(120,100));bar.addView(r,new LinearLayout.LayoutParams(120,100));bar.addView(t,new LinearLayout.LayoutParams(0,100,1));bar.addView(m,new LinearLayout.LayoutParams(120,100));l.addView(bar);
 w=new WebView(this);w.setBackgroundColor(Color.BLACK);WebSettings s=w.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);s.setAllowContentAccess(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);w.setWebViewClient(new WebViewClient());
 l.addView(w,new LinearLayout.LayoutParams(-1,0,1));setContentView(l);w.loadUrl(u);b.setOnClickListener(v->{if(w.canGoBack())w.goBack();});r.setOnClickListener(v->w.reload());m.setOnClickListener(v->{getSharedPreferences(P,0).edit().remove(K).apply();setup();});}
 @Override public void onBackPressed(){if(w!=null&&w.canGoBack())w.goBack();else super.onBackPressed();}
}
