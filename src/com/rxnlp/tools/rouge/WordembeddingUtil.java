package com.rxnlp.tools.rouge;

import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;
import org.apache.log4j.Logger;

import javax.xml.bind.SchemaOutputResolver;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

public class WordembeddingUtil{
    private static String SEP = "__";
    private HashMap<String, double[]> embedding;
    private static WordembeddingUtil wordembeddingUtil = null;
    static Logger logger = Logger.getLogger(ROUGECalculator.class);
    private HashMap<String, Double> idf;

    private WordembeddingUtil(){
        embedding = new HashMap<String, double[]>();
        idf = new HashMap<String, Double>();

        String path = ROUGECalculator.settings.EMBEDDING;
        String idfpath = "word2idflog10.txt";
        if(path.equals("")){
            logger.error("\nNo embedding path, please check your properties file!\n");
            System.exit(-1);
        }
        try {
            System.out.println("=====reading embeding=======");
            FileInputStream inputStream = new FileInputStream(path);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            /* read first line */
            String str = bufferedReader.readLine();
            String[] strs = str.split(" ");
            int dim = Integer.parseInt(strs[1]);
            System.out.println("=====words number: "+strs[0]+" dim: "+dim);
            while((str = bufferedReader.readLine()) != null){
                String[] lines = str.split(" ");
                double[] vec = new double[dim];
                if(lines.length > 2){
                    for(int i=0; i<dim; i++){
                        vec[i] = Double.parseDouble(lines[i+1]);
                    }
                    embedding.put(lines[0], vec);
                }else{
                    embedding.put(lines[0], null);
                }

            }
            bufferedReader.close();
            inputStream.close();
            /*System.out.println("=====embeding is loaded=======");
            System.out.println("=====reading idf======");
            FileInputStream idfstream = new FileInputStream(idfpath);
            BufferedReader idfbuffer = new BufferedReader(new InputStreamReader(idfstream));
            while((str = idfbuffer.readLine()) != null){
                String[] lines = str.split(" ");
                idf.put(lines[0], Double.parseDouble(lines[1]));
            }
            idfbuffer.close();
            idfstream.close();
            System.out.println("=====idf is loaded======");*/
        }catch (Exception e){
            logger.error("Can't find your embedding file! ");
            System.exit(-1);
        }

    }

    public static WordembeddingUtil getInstance(){
        if(wordembeddingUtil == null){
            wordembeddingUtil = new WordembeddingUtil();
        }
        return wordembeddingUtil;
    }

    /*no oov*/
    public double[] getVector(String ngram){
        double[] res = null;
        if(ROUGECalculator.settings.NGRAM == 1){
            res = this.embedding.get(ngram);
        }
        else if(ROUGECalculator.settings.NGRAM == 2){
            String[] grams = ngram.split(SEP);
            if(grams[0] == null || grams[1] == null)
                return res;
            String gram = grams[0] +"@$" +grams[1];
            res = this.embedding.get(gram);
        }
        return res;
    }

    /*avg for oov*/
    public double[] getAvgVector(String ngram) {
        double[] res = getVector(ngram);
        if (ROUGECalculator.settings.NGRAM >= 2 && res == null) {
            String[] grams = ngram.split(SEP);
            double[][] vec = new double[ROUGECalculator.settings.NGRAM][];
            int l = 0;
            for(int i=0; i<ROUGECalculator.settings.NGRAM; i++){
                vec[i] = this.embedding.get(grams[i]);
                if(vec[i] != null && l == 0) l = vec[i].length;
            }
            res = new double[l];
            int count = 0;
            for(int i=0; i<ROUGECalculator.settings.NGRAM; i++){
                if(vec[i] != null){
                    count++;
                    for (int j = 0; j < res.length; j++) {
                        res[j] += vec[i][j];
                    }
                }
            }
            if(count == 0) return null;
            for(int i=0; i<res.length; i++){
                res[i] /= count;
            }
           /* double[] vec1 = this.embedding.get(grams[0]);
            double[] vec2 = this.embedding.get(grams[1]);
            if (vec1 != null && vec2 != null) {
                res = new double[vec1.length];
                for (int i = 0; i < res.length; i++) {
                    res[i] = (vec1[i] + vec2[i]) / 2;
                }
            } else if (vec1 != null) {
                res = vec1;
            } else if (vec2 != null) {
                res = vec2;
            }*/
        }

        return res;
    }

    /*attention avg for oov*/
    public double[] getAttVector(String ngram) {
        double[] res = getVector(ngram);
        if (ROUGECalculator.settings.NGRAM == 2 && res == null) {
            String[] grams = ngram.split(SEP);
            double[] vec1 = this.embedding.get(grams[0]);
            double[] vec2 = this.embedding.get(grams[1]);
            if (vec1 != null && vec2 != null) {
                res = new double[vec1.length];
                for (int i = 0; i < res.length; i++) {
                    Double w1 = idf.get(grams[0]);
                    Double w2 = idf.get(grams[1]);
                    if(w1 != null && w2 != null){
                        double s = Math.exp(w1)+Math.exp(w2);
                        w1 = Math.exp(w1)/s;
                        w1 = Math.exp(w1)/s;
                        res[i] = vec1[i]*w1 + vec2[i]*w2;
                    }else{
                        res[i] = vec1[i]*0.5 + vec2[i]*0.5;
                    }
                }
            } else if (vec1 != null) {
                res = vec1;
            } else if (vec2 != null) {
                res = vec2;
            }
        }
        return res;
    }

    /*multiplicative for oov */
    public double[] getMulVector(String ngram){
        double[] res = getVector(ngram);
        if (ROUGECalculator.settings.NGRAM == 2 && res == null) {
            String[] grams = ngram.split(SEP);
            double[] vec1 = this.embedding.get(grams[0]);
            double[] vec2 = this.embedding.get(grams[1]);
            if (vec1 != null && vec2 != null) {
                res = new double[vec1.length];
                for (int i = 0; i < res.length; i++) {
                    res[i] = vec1[i] * vec2[i];
                }
            } else if (vec1 != null) {
                res = vec1;
            } else if (vec2 != null) {
                res = vec2;
            }
        }
        return res;
    }

    /*catenation  for oov*/
    public double[] getCatVector(String ngram){
        double[] res = null;
        if(ROUGECalculator.settings.NGRAM == 1){
            res = this.embedding.get(ngram);
        } else if(ROUGECalculator.settings.NGRAM == 2){
            String[] grams = ngram.split(SEP);
            String gram = grams[0] +"@$" +grams[1];
            double[] vec1 = this.embedding.get(grams[0]);
            double[] vec2 = this.embedding.get(grams[1]);
            if (vec1 != null && vec2 != null) {
                res = new double[vec1.length+vec2.length];
                for(int i=0; i<vec1.length; i++){
                    res[i] = vec1[i];
                    res[vec1.length+i] = vec2[i];
                }
            }else if (vec1 != null){
                res = new double[vec1.length*2];
                for(int i=0; i<vec1.length; i++){
                    res[i] = vec1[i];
                    res[vec1.length+i] = vec1[i];
                }
            }else if (vec2 != null){
                res = new double[vec2.length*2];
                for(int i=0; i<vec2.length; i++){
                    res[i] = vec2[i];
                    res[vec2.length+i] = vec2[i];
                }
            }

        }
        return res;
    }

   /* public static void main(String[] args) {
        WordembeddingUtil wu = new WordembeddingUtil();
        System.out.println(wu.getVector("grants").toString());
    }*/
}
