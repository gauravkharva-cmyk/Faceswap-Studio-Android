package com.gk.vedagyan;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class MainActivity extends Activity {
    private static final int DARK=Color.rgb(40,34,72);
    private static final int TEXT=Color.rgb(48,41,46);
    private static final int CREAM=Color.rgb(255,251,242);
    private static final int ACCENT=Color.rgb(166,88,22);
    private static final String[] VEDAS={"सभी वेद","ऋग्वेद","यजुर्वेद","सामवेद","अथर्ववेद"};
    private static final String[] KEYS={"","rig","yajur","sama","atharva"};
    private static final int PAGE_SIZE=30;
    private SQLiteDatabase db;
    private LinearLayout root, resultPanel;
    private Spinner vedaSelect,sectionSelect;
    private EditText query;
    private TextView countView;
    private Button backPage,nextPage;
    private final List<String> sections=new ArrayList<>();
    private int page=0;
    private int selectedVeda=0;
    private String selectedSection="सभी खण्ड";
    private String searchText="";
    private boolean showFavorites=false, showingDetail=false;
    private SharedPreferences prefs;
    private Set<String> favorites=new HashSet<>();
    private int textSize=19;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(DARK);
        prefs=getSharedPreferences("gk_veda_saved",MODE_PRIVATE);
        favorites=new HashSet<>(prefs.getStringSet("bookmarks",Collections.emptySet()));
        textSize=prefs.getInt("textSize",19);
        try {
            File local=new File(getFilesDir(),"gk-veda-corpus-v4.db");
            if (!local.exists() || local.length()<10000) {
                try (InputStream in=getAssets().open("corpus.db");
                     FileOutputStream out=new FileOutputStream(local)) {
                    byte[] bytes=new byte[65536]; int n;
                    while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
                }
            }
            db=SQLiteDatabase.openDatabase(local.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
            showHome();
        } catch(Exception ex) {
            root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(25),dp(20),dp(15));
            setContentView(root);addText(root,"पाठ-संग्रह खोलने में समस्या",23,true,DARK);
            addText(root,ex.toString(),16,false,TEXT);
            addText(root,"Created By : Gaurav Kharva",12,false,ACCENT);
        }
    }
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    private LinearLayout vertical(){
        LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;
    }
    private LinearLayout horizontal(){
        LinearLayout h=new LinearLayout(this);h.setOrientation(LinearLayout.HORIZONTAL);h.setGravity(Gravity.CENTER_VERTICAL);return h;
    }
    private TextView addText(LinearLayout parent,String content,int size,boolean bold,int color){
        TextView t=new TextView(this);t.setText(content);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(dp(3),dp(7),dp(3),dp(7));parent.addView(t);return t;
    }
    private Button button(String title){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);return b;
    }
    private void createBase(){
        root=vertical();root.setBackgroundColor(CREAM);
        root.setPadding(dp(12),dp(10),dp(12),dp(8));setContentView(root);
    }
    private void addCredit(){
        TextView credit=addText(root,"Created By : Gaurav Kharva",12,false,ACCENT);
        credit.setGravity(Gravity.END);
    }
    private void showHome(){
        showingDetail=false;createBase();
        addText(root,"ॐ  वेद ज्ञान",28,true,DARK);
        addText(root,"चार वेद • संस्कृत मूल पाठ • पूरी तरह ऑफलाइन",13,false,ACCENT);
        query=new EditText(this);query.setSingleLine(true);query.setText(searchText);
        query.setTextSize(16);query.setHint("मन्त्र, सूक्त या कोई शब्द खोजें");root.addView(query);
        LinearLayout line=horizontal();
        vedaSelect=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,VEDAS);
        vedaSelect.setAdapter(adapter);vedaSelect.setSelection(selectedVeda);
        line.addView(vedaSelect,new LinearLayout.LayoutParams(0,dp(52),1));
        sectionSelect=new Spinner(this);
        line.addView(sectionSelect,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(line);
        LinearLayout controls=horizontal();
        Button searchButton=button("खोजें");controls.addView(searchButton,new LinearLayout.LayoutParams(0,dp(50),1));
        Button saveButton=button(showFavorites?"सभी देखें":"★ सहेजे");
        controls.addView(saveButton,new LinearLayout.LayoutParams(0,dp(50),1));
        Button smaller=button("A−");controls.addView(smaller,new LinearLayout.LayoutParams(dp(48),dp(50)));
        Button larger=button("A+");controls.addView(larger,new LinearLayout.LayoutParams(dp(48),dp(50)));
        root.addView(controls);
        countView=addText(root,"संग्रह तैयार है",13,false,TEXT);
        ScrollView scroll=new ScrollView(this);resultPanel=vertical();scroll.addView(resultPanel);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout pager=horizontal();
        backPage=button("← पिछला");nextPage=button("अगला →");
        pager.addView(backPage,new LinearLayout.LayoutParams(0,dp(46),1));
        pager.addView(nextPage,new LinearLayout.LayoutParams(0,dp(46),1));
        root.addView(pager);addCredit();
        searchButton.setOnClickListener(v->{
            searchText=query.getText().toString().trim();page=0;showFavorites=false;
            showHome();
        });
        saveButton.setOnClickListener(v->{
            searchText=query.getText().toString().trim();showFavorites=!showFavorites;page=0;showHome();
        });
        smaller.setOnClickListener(v->{textSize=Math.max(15,textSize-1);prefs.edit().putInt("textSize",textSize).apply();refresh();});
        larger.setOnClickListener(v->{textSize=Math.min(30,textSize+1);prefs.edit().putInt("textSize",textSize).apply();refresh();});
        backPage.setOnClickListener(v->{page=Math.max(0,page-1);refresh();});
        nextPage.setOnClickListener(v->{page++;refresh();});
        vedaSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> p){}
            public void onItemSelected(AdapterView<?> p,View view,int position,long id){
                selectedVeda=position;page=0;
                updateSections();refresh();
            }
        });
    }
    private void updateSections(){
        String previous=selectedSection;
        sections.clear();sections.add("सभी खण्ड");
        if(selectedVeda>0){
            try(Cursor c=db.rawQuery("SELECT section FROM verse WHERE veda=? GROUP BY section ORDER BY min(section_no)",new String[]{KEYS[selectedVeda]})){
                while(c.moveToNext())sections.add(c.getString(0));
            }
        }
        ArrayAdapter<String> list=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sections);
        sectionSelect.setAdapter(list);
        int position=sections.indexOf(previous);sectionSelect.setSelection(Math.max(position,0));
        sectionSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> p){}
            public void onItemSelected(AdapterView<?> p,View v,int pos,long id){
                selectedSection=sections.get(pos);page=0;refresh();
            }
        });
    }
    private String whereClause(List<String> args){
        ArrayList<String> rules=new ArrayList<>();
        if(selectedVeda>0){rules.add("veda=?");args.add(KEYS[selectedVeda]);}
        if(!selectedSection.equals("सभी खण्ड")&&selectedVeda>0){
            rules.add("section=?");args.add(selectedSection);
        }
        if(!searchText.isEmpty()){
            rules.add("(reference LIKE ? OR sanskrit LIKE ? OR section LIKE ?)");
            String pattern="%"+searchText+"%";
            args.add(pattern);args.add(pattern);args.add(pattern);
        }
        if(showFavorites){
            if(favorites.isEmpty())rules.add("1=0");
            else{
                StringBuilder q=new StringBuilder("id IN (");
                int i=0;for(String f:favorites){if(i++>0)q.append(',');q.append('?');args.add(f);}
                q.append(')');rules.add(q.toString());
            }
        }
        return rules.isEmpty()?"":" WHERE "+String.join(" AND ",rules);
    }
    private void refresh(){
        if(resultPanel==null||showingDetail)return;
        resultPanel.removeAllViews();
        ArrayList<String> args=new ArrayList<>();
        String where=whereClause(args);
        int total=0;
        try(Cursor c=db.rawQuery("SELECT count(*) FROM verse"+where,args.toArray(new String[0]))){
            if(c.moveToFirst())total=c.getInt(0);
        }
        int start=page*PAGE_SIZE;
        if(start>=total&&page>0){page=0;start=0;}
        countView.setText("मन्त्र संग्रह: "+total+"  • पृष्ठ "+(page+1)+
            "  • हिन्दी अर्थ जहाँ सत्यापित हैं वहीं उपलब्ध");
        backPage.setEnabled(page>0);
        nextPage.setEnabled(start+PAGE_SIZE<total);
        List<String> paging=new ArrayList<>(args);
        paging.add(String.valueOf(PAGE_SIZE));paging.add(String.valueOf(start));
        String sql="SELECT id,reference,section,substr(sanskrit,1,95) FROM verse"+where+" ORDER BY id LIMIT ? OFFSET ?";
        try(Cursor c=db.rawQuery(sql,paging.toArray(new String[0]))){
            while(c.moveToNext()){
                final int id=c.getInt(0);
                LinearLayout item=vertical();item.setPadding(dp(7),dp(9),dp(7),dp(9));
                TextView caption=addText(item,c.getString(1)+" • "+c.getString(2),15,true,DARK);
                String first=c.getString(3).replace("\n"," ");
                addText(item,first+(first.length()>=95?"…":""),textSize,false,TEXT);
                item.setOnClickListener(v->showVerse(id));
                resultPanel.addView(item);
                View border=new View(this);border.setBackgroundColor(0xFFE3D9CC);
                resultPanel.addView(border,new LinearLayout.LayoutParams(-1,dp(1)));
            }
        }
        if(total==0)addText(resultPanel,"यहाँ कोई मन्त्र नहीं मिला। वेद या खोज बदलें।",17,false,TEXT);
    }
    private void showVerse(int id){
        showingDetail=true;
        String ref,section,text,meaning,commentary,source;
        try(Cursor c=db.rawQuery("SELECT reference,section,sanskrit,meaning,commentary,source FROM verse WHERE id=?",
                                 new String[]{String.valueOf(id)})){
            if(!c.moveToFirst())return;
            ref=c.getString(0);section=c.getString(1);text=c.getString(2);
            meaning=c.getString(3);commentary=c.getString(4);source=c.getString(5);
        }
        createBase();
        Button back=button("← सभी मन्त्र");root.addView(back);back.setOnClickListener(v->showHome());
        ScrollView scroll=new ScrollView(this);LinearLayout content=vertical();content.setPadding(dp(5),0,dp(5),dp(12));
        scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        addText(content,ref,24,true,DARK);addText(content,section,15,false,ACCENT);
        addText(content,"मूल संस्कृत मन्त्र",16,true,DARK);
        TextView words=addText(content,text,textSize+2,false,TEXT);words.setLineSpacing(dp(5),1.1f);
        addText(content,"हिन्दी अर्थ",16,true,DARK);
        addText(content,meaning.isEmpty()?"इस मन्त्र का सत्यापित हिन्दी अर्थ अभी उपलब्ध नहीं है।":meaning,
                18,false,TEXT);
        addText(content,"विस्तृत हिन्दी व्याख्या",16,true,DARK);
        addText(content,commentary.isEmpty()?"इस मन्त्र की व्यक्तिगत शोध-समीक्षित व्याख्या अभी जोड़ी नहीं गई है।":commentary,
                17,false,TEXT);
        addText(content,"पाठ स्रोत: "+source,12,false,ACCENT);
        addText(content,"पाठ-परम्परा और शाखाओं में भेद हो सकते हैं। यह केवल अध्ययन हेतु है।",12,false,ACCENT);
        Button star=button(favorites.contains(String.valueOf(id))?"★ सहेजा गया":"☆ पसन्दीदा में जोड़ें");
        content.addView(star);
        star.setOnClickListener(v->{
            String key=String.valueOf(id);
            if(favorites.contains(key))favorites.remove(key);else favorites.add(key);
            prefs.edit().putStringSet("bookmarks",new HashSet<>(favorites)).apply();
            star.setText(favorites.contains(key)?"★ सहेजा गया":"☆ पसन्दीदा में जोड़ें");
        });
        addCredit();
    }
    @Override public void onBackPressed(){
        if(showingDetail)showHome(); else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(db!=null)db.close();super.onDestroy();
    }
}
