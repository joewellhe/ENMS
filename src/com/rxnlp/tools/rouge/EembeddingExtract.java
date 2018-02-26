package com.rxnlp.tools.rouge;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class EembeddingExtract {

    private static ROUGESettings settings;
    private static String SEP = "__";

    /**
     * Get list of n-grams
     *
     * @param sents
     * @return
     */
    public Collection<String> getNGramTokens(List<String> sents) {

        Collection<String> ngramList = null;

        return ngramList;
    }

    public static void main(String[] args) throws IOException {
        File dictout = new File("small_embedding_ngram");
        BufferedWriter dw = new BufferedWriter(new FileWriter(dictout));

        settings = new ROUGESettings();
        SettingsUtil.loadProps(settings);
        ROUGECalculator rc = new ROUGECalculator();
        rc.settings = settings;

        String projectDir = settings.PROJ_DIR;
        String references = projectDir + "/reference";
        String system = projectDir + "/system";

        Path refPath = Paths.get(references);
        Path sysPath = Paths.get(system);


        Map<String,double[]> dict = new HashMap<String,double[]>();

        if (Files.exists(refPath) && Files.exists(sysPath)) {
            List<Path> allFiles = Files.list(refPath).collect(Collectors.toList());
            List<Path> sysFiles = Files.list(sysPath).collect(Collectors.toList());
            allFiles.addAll(sysFiles);

            for(Path path: allFiles){
                List<String> sens = rc.getSystemSents(path);
                int n = settings.NGRAM;
                for(int i = 1; i<=n ; i++){
                    settings.NGRAM = i;
                    Collection<String> tokens = rc.getNGramTokens(sens);
                    for(String w : tokens){
                        if(i == 1) {
                            dict.put(w, null);
                        }else{
                            w = w.replace(SEP, "@$");
                            w = w.substring(0, w.length()-2);
                            dict.put(w, null);
                        }
                    }
                }

            }


        }
        // path of word embedding
        //int size1 = ReadEmbedding("F:\\paragram_300_ws353\\paragram_300_ws353\\paragram_300_ws353.txt",dict);
        int size2 = ReadEmbedding("F:\\sgns.words",dict);
        int size = size2;
        dw.write(String.valueOf(size));
        dw.write("\r\n");
        for(Entry<String,double[]> entry:dict.entrySet()){
            if(entry.getValue()!=null){
                dw.write(entry.getKey());
                //System.out.print(entry.getKey());
                for(int i=0;i<size;i++){
                    dw.write(String.valueOf(" "+entry.getValue()[i]));
                    //System.out.print(" "+entry.getValue()[i]);
                }
                dw.write("\r\n");
            }
        }
        dw.flush();
        dw.close();

       /* // TODO Auto-generated method stub
        String[] filepath={"STS2015.input.answers-forums.nosymbol.txt","STS2015.input.answers-students.nosymbol.txt","STS2015.input.belief.nosymbol.txt","STS2015.input.headlines.nosymbol.txt","STS2015.input.images.nosymbol.txt"};
        //,"STS2014.input.OnWN.stopwords","STS2014.input.images.stopwords","STS2014.input.tweet-news.stopwords"
        for(int m=0;m<filepath.length;m++){
            File readFile = new File(filepath[m]);
            File dictout = new File("D2V_dict_300_"+filepath[m]);
            BufferedWriter dw = new BufferedWriter(new FileWriter(dictout));
            Map<String,double[]> dict=new HashMap<String,double[]>();
            if(readFile.length()>0){

                BufferedReader reader = new BufferedReader(new FileReader(readFile));
                try{
                    String line = null;
                    //一行一行的读入
                    while(((line = reader.readLine()) != null)){
                        StringTokenizer td = new StringTokenizer(line," ");
                        while(td.hasMoreTokens()){
                            String tempword = td.nextToken();
                            if(tempword.matches(".*\\d+.*")){
                                continue;
                            }
                            else{
                                // String  removeword = tempword.replaceAll("\\pP|\\pS", "");
                                if(tempword.length()!=0){
                                    dict.put(tempword, null);
                                    //System.out.println("dict = "+removeword.toLowerCase());
                                }
                            }
                        }
                    }
                    reader.close();
                }catch(IOException e){
                    e.printStackTrace();
                }
            }else{
                System.out.println("Read File failed , Exit!");
                System.exit(1);
            }

            // 词典路径
            int size = ReadEmbedding("dict2vec-vectors-dim300.vec",dict);
            dw.write(String.valueOf(size));
            dw.write("\r\n");
            for(Entry<String,double[]> entry:dict.entrySet()){
                if(entry.getValue()!=null){
                    dw.write(entry.getKey());
                    //System.out.print(entry.getKey());
                    for(int i=0;i<size;i++){
                        dw.write(String.valueOf(" "+entry.getValue()[i]));
                        //System.out.print(" "+entry.getValue()[i]);
                    }
                    dw.write("\r\n");
                }
            }
            dw.flush();
            dw.close();
        }*/
    }

    /**
     * 生成对应的数据集词典
     * @throws FileNotFoundException
     */
    private static int ReadEmbedding(String file_name,Map<String,double[]> dict) throws FileNotFoundException {
        File readFile = new File(file_name);
        int wordNum;
        int size;
        //System.out.println("jinlaile");
        if(readFile.length()>0){
            BufferedReader reader = new BufferedReader(new FileReader(readFile));
            try{
                //System.out.println("jinlaile");
                String line = null;
                line = reader.readLine();

                StringTokenizer t = new StringTokenizer(line," ");
                wordNum = Integer.parseInt(t.nextToken());

                size = Integer.parseInt(t.nextToken());
                // System.out.println(size);
                // double tmp[] = new double [size];
                for(int b=0;b<wordNum-1;b++){
                    double v[] = new double[size];
                    line = reader.readLine();
                    StringTokenizer st = new StringTokenizer(line," ");
                    String word = st.nextToken();
                    for(Entry<String,double[]> entry:dict.entrySet()){
                        if(word.equals(entry.getKey())){
                            if(entry.getValue()==null){
                                entry.setValue(new double[size]);
                                //v = entry.getValue();
                            }
                            // System.out.println("find dict = "+entry.getKey());
                            for(int a=0;a<size;a++){

                                v[a] = Double.parseDouble(st.nextToken());
                            }
                            entry.setValue(v);
                            break;
                        }
                    }
                }
                reader.close();
                return size;
            }catch(IOException e){
                e.printStackTrace();
                return -1;
            }
        }
        else{
            System.out.println("Read Embedding File failed , Exit!");
            return -1;
        }
    }
}
