package com.rxnlp.tools.rouge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.log4j.Logger;
import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.englishStemmer;

import com.rxnlp.tools.rouge.ROUGESettings.RougeType;
import com.rxnlp.utils.lang.StopWordsHandler;
import com.rxnlp.utils.lang.WordNetDict;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;

/**
 * 
 * @author Kavita Ganesan
 * @version ROUGE 2.0
 * add soft rouge
 * improved by joewellhe
 *
 */
public class ROUGECalculator {

	DecimalFormat df = new DecimalFormat("0.00000");

	static Logger logger = Logger.getLogger(ROUGECalculator.class);
	static MaxentTagger tagger;
	static SnowballStemmer stemmer;

	private String POS_SEP = "(_|/|#)";
	private static String SEP = "__";

	WordNetDict wrdnet = new WordNetDict();
	private StopWordsHandler stopHandler;
	public static ROUGESettings settings;
	BufferedWriter resultsWriter;

	
	public class Result {

		double precision = 0;
		double recall = 0;
		double f = 0;
		int count = 0;
		String name;

	}

	public class TaskFile {

		String taskName;
		HashMap<Path, Result> systemFiles = new HashMap<Path, Result>();
		List<Path> referenceFiles = new ArrayList<Path>();

		@Override
		public boolean equals(Object obj) {
			TaskFile tf1 = (TaskFile) obj;
			return tf1.taskName.equals(taskName);
		}

		@Override
		public int hashCode() {
			return taskName.hashCode();
		}

	}

	public static void main(String args[]) {

		ROUGECalculator rc = new ROUGECalculator();
		rc.start(args);
	}

	private void start(String[] args) {

		settings = new ROUGESettings();
		SettingsUtil.loadProps(settings);
		if(args.length > 0) {
			settings.ALPHA = Double.parseDouble(args[0]);
			settings.RESULTS_FILE = "result_" + (settings.ALPHA * 100) + ".txt";
		}
		if (settings.REMOVE_STOP_WORDS) {
			stopHandler = new StopWordsHandler(settings.STOP_WORDS_FILE);
		}
		
		if(settings.USE_STEMMER){
			try {
				Class stemClass = Class.forName("org.tartarus.snowball.ext." +settings.STEMMER );
				stemmer = (SnowballStemmer) stemClass.newInstance();
			} catch (ClassNotFoundException e) {
				logger.error("Stemmer not found "+e.getMessage());
				logger.error("Default englishStemmer will be used");
				stemmer=new englishStemmer();
			} catch (InstantiationException e) {
				logger.error("Problem instantiating stemmer..."+e.getMessage());
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		/** load POS tagger only if ROUGE topic or synonyms are true */
		if (settings.ROUGE_TYPE.equals(RougeType.topic) || settings.ROUGE_TYPE.equals(RougeType.topicUniq) 	|| settings.USE_SYNONYMS) {
			tagger = new MaxentTagger("resources/taggers/" + settings.TAGGER_NAME);
			logger.info("Loaded...POS tagger.");
		}

		/*
		 * if(args.length <1) { System.err.println(
		 * "Please specify project directory. "); System.exit(-1); }
		 * 
		 * if(args.length<2){ printUsage(); }
		 */

		if (settings.OUTPUT_TYPE.equalsIgnoreCase("file")) {
			try {

				resultsWriter = new BufferedWriter(new FileWriter(settings.RESULTS_FILE));
				resultsWriter.write("ROUGE-Type");
				resultsWriter.write(",");
				resultsWriter.write("Task Name");
				resultsWriter.write(",");
				resultsWriter.write("System Name");
				resultsWriter.write(",");
				resultsWriter.write("Avg_Recall");
				resultsWriter.write(",");
				resultsWriter.write("Avg_Precision");
				resultsWriter.write(",");
				resultsWriter.write("Avg_F-Score");
				resultsWriter.write(",");
				resultsWriter.write("Num Reference Summaries");
				resultsWriter.newLine();

			} catch (IOException e) {
				logger.warn("There was a problem creating the results file ", e.getCause());
				e.printStackTrace();
			}
		}

		String projectDir = settings.PROJ_DIR;
		String references = projectDir + "/reference";
		String system = projectDir + "/system";

		HashMap<String, TaskFile> hmEvalTasks = new HashMap<String, TaskFile>();

		Path refPath = Paths.get(references);
		Path sysPath = Paths.get(system);

		try {
			if (Files.exists(refPath) && Files.exists(sysPath)) {
				List<Path> refFiles = Files.list(refPath).collect(Collectors.toList());
				List<Path> sysFiles = Files.list(sysPath).collect(Collectors.toList());
				initTasks(refFiles, sysFiles, hmEvalTasks);
				evaluate(hmEvalTasks);
			} else {
				logger.error("A valid 'references' and 'system' folder not found..Check rouge.properties file.");
				logger.error("\nYou need to have a valid 'references' and 'system' folder under " + projectDir
						+ ". Please check your rouge.properties file");
				System.exit(-1);
			}
		} catch (Exception e) {
			System.out.print("========================");
			logger.error("========"+Files.exists(refPath));
			logger.error("========"+Files.exists(sysPath));
			e.printStackTrace();
			logger.error(e.getMessage());
			logger.error("\nYou need to have a valid 'references' and 'system' folder under " + projectDir
					+ ". Please check your rouge.properties file");
			System.exit(-1);
		}
	}



		/*List<String> reference=new ArrayList<String>();
		List<String> candidate=new ArrayList<String>();


		reference.add("I really loved the sound quality it was very good.");
		reference.add("Battery lasted long, but what is bad that is that it is so expensive.");


		candidate.add("Battery lasted super long.");
		candidate.add("Sound quality extremely good.");
		candidate.add("Battery was expensive.");

		if(ROUGESettings.TYPE.equals(RougeType.topic) || 
			ROUGESettings.TYPE.equals(RougeType.topicCompressed)){

			getPOSTagged(reference);
			getPOSTagged(candidate);
		}

		computeRouge(reference,candidate);*/

	

	private void evaluate(HashMap<String, TaskFile> hmEvalTasks) {

		Set<String> tasks = hmEvalTasks.keySet();
		StringBuffer b = new StringBuffer();

		Collection<String> wordlist = new HashSet<String>();

		System.out.println("\n========Results=======\n");
		for (String task : tasks) {

			TaskFile t = hmEvalTasks.get(task);
			HashMap<Path, Result> systems = t.systemFiles;
			Set<Path> sysKeys = systems.keySet();

			for (Path system : sysKeys) {
				// get all sentences from the system file
				List<String> systemSents = getSystemSents(system);

				// the result object for sys file
				Result r = systems.get(system);

				computeRouge(r, systemSents, t.referenceFiles);
				String str = getROUGEName(settings);

				// Print results to console

				System.out.println(str + "\t" + t.taskName.toUpperCase() + "\t" + r.name.toUpperCase() + "\tAverage_R:"
						+ df.format(r.recall) + "\tAverage_P:" + df.format(r.precision) + "\tAverage_F:"
						+ df.format(r.f) + "\tNum Reference Summaries:" + r.count);

				// Write to file if specified by settings
				if (resultsWriter != null) {
					try {
						b.delete(0, b.length());
						b.append(str).append(",").append(t.taskName.toUpperCase()).append(",")
								.append(r.name.toUpperCase()).append(",").append(df.format(r.recall)).append(",")
								.append(df.format(r.precision)).append(",").append(df.format(r.f)).append(",")
								.append(r.count);

						resultsWriter.write(b.toString());
						resultsWriter.newLine();

					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
			}
			try {
				File file = new File("wordlist");
				PrintStream ps = new PrintStream(new FileOutputStream(file));
				for(String str:wordlist){
					ps.println(str);
				}
				ps.close();

			}catch (Exception e){
				e.printStackTrace();
			}

		}
		System.out.println("\n======Results End======\n");
		cleanUp();
	}

	private void cleanUp() {
		// Close output file
		try {
			if (resultsWriter != null) {
				logger.info("Results written to " + settings.RESULTS_FILE);
				System.out.println("\n\nPlease find results file in: " + settings.RESULTS_FILE);
				resultsWriter.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private String getROUGEName(ROUGESettings settings) {

		StringBuffer sb = new StringBuffer();
		if(settings.ROUGE_METRIC.equals("N"))
			sb.append("ROUGE-" + settings.NGRAM);
		else
			sb.append("ROUGE-" + settings.ROUGE_METRIC);
		// TYPE - TOPIC, NORMAL
		if (!settings.ROUGE_TYPE.equals(RougeType.normal))
			sb.append("-").append(settings.ROUGE_TYPE);

		if (settings.REMOVE_STOP_WORDS)
			sb.append("+").append("StopWordRemoval");

		if (settings.USE_STEMMER)
			sb.append("+").append("Stemming");

		if (settings.USE_SYNONYMS)
			sb.append("+").append("Synonyms");

		return sb.toString();
	}

	public List<String> getSystemSents(Path system) {
		/*
		 * try { BufferedReader r=new BufferedReader(new FileReader(system));
		 * List<String> sentList=new ArrayList<>(); String line="";
		 * 
		 * while((line=r.readLine())!=null){ line=cleanSent(line);
		 * sentList.add(line); }
		 */

		// read file into stream, try-with-resources
		List<String> sentList = new ArrayList<>();
		try (Stream<String> stream = Files.lines(system,StandardCharsets.ISO_8859_1)) {

			stream.forEach(line -> {
				line = cleanSent(line);
				sentList.add(line);
			});

		} catch (IOException e) {
			logger.error("Problem reading system sentences" );
		}

		return sentList;
		/*
		 * } catch (IOException e) { e.printStackTrace(); return null; }
		 */
	}

	private String cleanSent(String line) {
		line = line.replaceAll("[0-9]+", " @ ").toLowerCase();
		line = line.replaceAll("(!|\\.|\"|'|;|:|,)", " ").toLowerCase();

		return line;
	}

	private void computeRouge(String system, List<String> referenceFiles) {

	}

	private static void initTasks(List<Path> refFiles, List<Path> sysFiles, HashMap<String, TaskFile> hmEvalTasks) {

		// INITIALIZE SYSTEM FILES FOR EACH TASK
		for (Path sysFile : sysFiles) {

			String fileName = sysFile.getFileName().toFile().getName();
			String[] fileToks = fileName.split("_");

			if (fileNamingOK(fileToks, fileName)) {
				TaskFile theEvalTask = getEvalTask(fileToks, hmEvalTasks);

				Result r = new ROUGECalculator().new Result();
				r.name = fileToks[1];

				theEvalTask.systemFiles.put(sysFile, r);
			}

		}

		// INITIALIZE REFERENCE FILES FOR EACH TASK
		for (Path refFile : refFiles) {

			String fileName = refFile.getFileName().toFile().getName();
			String[] fileToks = fileName.split("_");

			if (fileNamingOK(fileToks, fileName)) {
				TaskFile theEvalTask = getEvalTask(fileToks, hmEvalTasks);
				theEvalTask.referenceFiles.add(refFile);
			}
		}

	}

	/**
	 * Each unique text, is considered a unique summarization task. Create a
	 * summarization task and add evaluation tasks into hmEvalTasks
	 * 
	 * @param fileToks
	 * @param hmEvalTasks
	 * @return
	 */
	private static TaskFile getEvalTask(String[] fileToks, HashMap<String, TaskFile> hmEvalTasks) {
		String taskName = fileToks[0].toLowerCase();
		TaskFile theEvalTask = hmEvalTasks.get(taskName);

		if (theEvalTask == null) {
			theEvalTask = new ROUGECalculator().new TaskFile();
			theEvalTask.taskName = taskName;
			hmEvalTasks.put(taskName, theEvalTask);
		}
		return theEvalTask;
	}

	private static boolean fileNamingOK(String[] fileToks, String fileName) {
		if (fileToks.length < 2) {
			logger.error("Something seems to be wrong with your file naming convention: " + fileName);
			System.exit(-1);
		}
		return true;
	}

	private void getPOSTagged(List<String> reference, boolean restrictByTopic) {

		List<String> newList = new ArrayList<String>();
		for (String sent : reference) {
			sent = sent.toLowerCase();
			String tagged = tagger.tagString(sent);

			if (restrictByTopic)
				tagged = getRelevantPOS(tagged.toLowerCase());

			if (tagged.trim().length() > 0)
				newList.add(tagged.toLowerCase());
		}

		reference.clear();
		reference.addAll(newList);
	}

	/**
	 * Pick out words that have the relevant POS tags if ROUGE-Topic selected
	 * 
	 * @param tagged
	 * @return
	 */
	private String getRelevantPOS(String tagged) {
		String[] tokens = tagged.split("\\s+");
		StringBuffer b = new StringBuffer();

		for (String t : tokens) {
			if (t.matches(".*(" + settings.SELECTED_TOPICS + ").*")) {
				// b.append(t.split("_")[0]).append(" ");
				b.append(t).append(" ");
			}
		}
		return b.toString().trim();
	}

	void getCombiFor(List<HashSet<String>> li, List<String> daList) {

		for (int i = 0; i < li.size(); i++) {

			ArrayList<String> toRemove = new ArrayList<String>();
			ArrayList<String> toAdd = new ArrayList<String>();

			HashSet<String> hs = li.get(i);

			if (daList.size() > 0) {

				for (String tempStr : daList) {
					for (String s : hs) {
						toRemove.add(tempStr);
						toAdd.add(tempStr + SEP + s);
						// tempStr=tempStr+"$$;"+s;
					}
				}

			} else {
				daList.addAll(hs);
			}

			daList.addAll(toAdd);
			daList.removeAll(toRemove);
		}
	}

	public void computeRouge(Result r, List<String> candSents, List<Path> refSentFileNames) {

		List<String> refSents = null;

		// For each reference summary
		for (Path refSentFile : refSentFileNames) {

			refSents = getSystemSents(refSentFile);

			if (refSents == null) {

				logger.warn("No reference sentences in " + refSentFile);

			} else {

				/** ROUGE topic , POS TAG and set n-gram to 1 */
				if (settings.ROUGE_TYPE.equals(RougeType.topic) || settings.ROUGE_TYPE.equals(RougeType.topicUniq)) {
					settings.NGRAM = 1;
					getPOSTagged(refSents, true);
					getPOSTagged(candSents, true);
				}

				/** ROUGE+Synonyms, POS TAG */
				else if (settings.USE_SYNONYMS) {
					getPOSTagged(refSents, false);
					getPOSTagged(candSents, false);
				}

				/** Remove Stop Words */
				if (settings.REMOVE_STOP_WORDS) {
					removeStopWords(refSents);
					removeStopWords(candSents);
				}
				
				/** Stem Words */
				if (settings.USE_STEMMER) {
					applyStemming(refSents);
					applyStemming(candSents);
				}

				double overlap = 0;
				double ROUGE = 0;

				/* ROUGE METRIC*/
				if(settings.ROUGE_METRIC.equals("N")){
					rougeN(r, overlap, candSents, refSents);
				}else if(settings.ROUGE_METRIC.equals("SU")){
					rougeSU(r, overlap, candSents, refSents);
				}else if(settings.ROUGE_METRIC.equals("L")){
					rougeL(r, overlap, candSents, refSents);
				}else if(settings.ROUGE_METRIC.equals("SEMI-RN")){
					semiRougeN(r, overlap, candSents, refSents);
				}else if(settings.ROUGE_METRIC.equals("SRN")){
					softRougeN(r, overlap, candSents, refSents);
				}else if(settings.ROUGE_METRIC.equals("SEMI-RSU")){
					semiRougeSU(r, overlap, candSents, refSents);
				}else if(settings.ROUGE_METRIC.equals("S-RSU")){
					softRougeSU(r, overlap, candSents, refSents);
				}else if(settings.ROUGE_METRIC.equals("SRL")){
					softRL(r, overlap, candSents, refSents);
				}else{
					logger.error("please check the rouge.metric parameter in your properties file!");
					System.exit(-1);
				}

			} // SENTS FOUND
		}

		// AVERAGE THE P/R (MACRO AVERAGE)
		r.precision = r.precision / r.count;
		r.recall = r.recall / r.count;

		if (r.precision > 0 || r.recall > 0)
			r.f = 2 * r.precision * r.recall / (r.precision + r.recall);

	}

	private void rearrange(Collection<String> originalTokens) {
		Collection<String> str = new ArrayList<String>();
		Collection<String> newStr = new ArrayList<String>();
		StringBuffer b = new StringBuffer();

		for (String ref : originalTokens) {
			List<String> theTokens = Arrays.asList(ref.split(SEP));
			Collections.sort(theTokens);

			for (String s : theTokens) {
				b.append(s).append(SEP);
			}
			newStr.add(b.toString());
			b.delete(0, b.length());
		}

		originalTokens.clear();
		originalTokens.addAll(newStr);

	}

	private void removePos(Collection<String> nGrams) {

		List<String> removeList = new ArrayList<String>();
		List<String> addList = new ArrayList<String>();

		for (String theNGram : nGrams) {

			// get individual words
			String[] words = theNGram.split(SEP);
			StringBuffer b = new StringBuffer();

			// for each individual word, get the word without POS tags
			// create n-gram tokens with SEP as the separator
			for (String w : words) {

				String[] wordPOSTokens = w.split(POS_SEP);
				if (wordPOSTokens.length == 2) {
					String wordToken = wordPOSTokens[0];
					String posToken = wordPOSTokens[1];
					b.append(wordToken.trim()).append(SEP);
				}
			}

			addList.add(b.substring(0, b.length() - SEP.length()).toString().trim());
			removeList.add(theNGram);
		}

		nGrams.addAll(addList);// add tokens without the POS tags
		nGrams.removeAll(removeList);// remove tokens with the POS tags. At this
										// point we only want the words and not
										// the POS tags
	}

	private HashSet<String> getSynonymList(String sysTok) {
		String[] toks = sysTok.split(POS_SEP);
		HashSet<String> synonymList = new HashSet<>();

		if (toks[1].contains("nn")) {
			synonymList = wrdnet.getNounSynonyms(toks[0]);
		}

		else if (toks[1].contains("jj")) {
			synonymList = wrdnet.getAdjectiveSynonyms(toks[0]);
		}

		else if (toks[1].contains("vb")) {
			synonymList = wrdnet.getVerbSynonyms(toks[0]);
		}

		// System.err.println(toks[0]+":"+synonymList);
		synonymList.add(toks[0].trim());
		return synonymList;
	}

	/*
	 * private double getOverlap(Collection<String> main, Collection<String>
	 * sub) {
	 * 
	 * double overlap=0;
	 * 
	 * for(String aa:main){ Collection<String> toksList=getNgramList(aa);
	 * 
	 * if(sub.contains(aa)){ overlap++; } }
	 * 
	 * overlap=overlap/main.size();
	 * 
	 * 
	 * return overlap; }
	 */

	/*
	 * private double getOverlap(List<String> sents) { for(String s:sents){
	 * Collection<String> hsss= getNgramList(s,true);
	 * 
	 * for(String s:hsss){ if(hsref.contains(s)){ overlap++; } } }
	 * 
	 * overlap=overlap/refSents.size(); return 0; }
	 */

	private void applyStemming(List<String> sents) {

		List<String> updatedSents = new ArrayList<String>();
		StringBuffer b = new StringBuffer();

		for (String s : sents) {
			b.delete(0, b.length());
			String[] tokens = s.toLowerCase().split("\\s+");

			for (String t : tokens) {

				String[] daToks = t.split("_");

				// IF NOT STOP WORD KEEP
				stemmer.setCurrent(daToks[0]);
				
				if(stemmer.stem()){
					b.append(stemmer.getCurrent());
					
					if(daToks.length==2){
						b.append(daToks[1]);
					}
					b.append(" ");
				}
			}

			updatedSents.add(b.toString().trim());
		}

		sents.clear();
		sents.addAll(updatedSents);
	}

	private void removeStopWords(List<String> sents) {

		List<String> updatedSents = new ArrayList<String>();
		StringBuffer b = new StringBuffer();

		for (String s : sents) {
			b.delete(0, b.length());
			String[] tokens = s.toLowerCase().split("\\s+");

			for (String t : tokens) {

				String[] daToks = t.split("_");

				/*
				 * //IF IT HAS POS TAGS, then get just the words
				 * if(t.contains("_")){ da }
				 */

				// IF NOT STOP WORD KEEP
				if(daToks.length < 1){
					System.out.println("=======");
					System.out.println(sents);
				}
				if (!stopHandler.isStop(daToks[0].trim())) {
					b.append(t).append(" ");
				}
			}

			updatedSents.add(b.toString().trim());
		}

		sents.clear();
		sents.addAll(updatedSents);
	}

	/**
	 * Get list of n-grams
	 * 
	 * @param sents
	 * @return
	 */
	public Collection<String> getNGramTokens(List<String> sents) {

		Collection<String> ngramList = null;

		if (settings.ROUGE_TYPE.equals(RougeType.topicUniq) || settings.ROUGE_TYPE.equals(RougeType.normalCompressed))
			ngramList = new HashSet<String>();
		else
			ngramList = new ArrayList<String>();

		for (String sent : sents) {
			// ROUGE-1
			if (settings.NGRAM == 1) {
				String[] tokens = sent.split("\\s+");
				ngramList.addAll(Arrays.asList(tokens));
			}

			// ROUGE-N
			if (settings.NGRAM > 1) {
				generateNgrams(settings.NGRAM, sent, ngramList);
			}
		}

		return ngramList;
	}

	private Collection<String> getSkipTokens(List<String> sents){
		Collection<String> skipList = new ArrayList<String>();
		for(String sent : sents){
			generateSkipgram(sent, skipList);
		}
		return skipList;
	}

	/*
	 * private static Collection<String> getNgramList(String sent,boolean
	 * traditional) {
	 * 
	 * Collection<String> ngramList=null;
	 * 
	 * if(ROUGESettings.TYPE.equals(RougeType.topicCompressed) ||
	 * ROUGESettings.TYPE.equals(RougeType.normalCompressed) ) ngramList=new
	 * HashSet<String>(); else ngramList=new ArrayList<String>();
	 * 
	 * 
	 * extractNgrams(sent,ngramList);
	 * 
	 * return ngramList; }
	 */

	private static void generateNgrams(int gram, String summary, Collection<String> ngramList) {
		String[] tokens = summary.split("\\s+");

		// GENERATE THE N-GRAMS
		for (int k = 0; k < (tokens.length - gram + 1); k++) {
			String s = "";
			int start = k;
			int end = k + gram;
			for (int j = start; j < end; j++) {
				s = s + tokens[j] + SEP;
			}
			ngramList.add(s);
		}

	}

	/**
	 * add by jiawei He
	 * @param summary
	 * @return a list contain all skip-gram
	 */
	private static void generateSkipgram(String summary, Collection<String> skipgramList ){
		String[] tokens = summary.split("\\s+");
		// GENERATE THE SKIP-GRAMS
		for(int  k = 0; k < (tokens.length - 4); k++){
			int start = k;
			int end = k + 4;
			for(int j = start+1; j < end; j++ ){
				String s = new String("");
				s = s + tokens[k] + SEP + tokens[j] + SEP;
				skipgramList.add(s);
			}
		}
	}

	public void rougeN(Result r, double overlap, List<String> candSents, List<String> refSents){
		Collection<String> hsrefOriginal = getNGramTokens(refSents);

		Collection<String> referenceSummaryTokens = getNGramTokens(refSents);
		Collection<String> systemSummaryTokens = getNGramTokens(candSents);

		HashSet<String> theSynonyms = new HashSet<>();

		/*
		 * if(s.LOOSEN_WORD_ORDER){ rearrange(referenceSummaryTokens);
		 * rearrange(systemSummaryTokens); }
		 */

		if (settings.USE_SYNONYMS){
			removePos(referenceSummaryTokens);

			// PERFORM COMPARISONS
			if (systemSummaryTokens.size() > 0 && referenceSummaryTokens.size() > 0) {
				for (String sysTok : systemSummaryTokens) {

					String[] unigramList = sysTok.split(SEP);

					// get list of synonyms for individual words in the
					// system summary
					List<HashSet<String>> listOfSynonyms = new ArrayList<HashSet<String>>();
					for (String unigram : unigramList) {
						theSynonyms = getSynonymList(unigram);
						listOfSynonyms.add(theSynonyms);
					}

					// combine synonyms (combinatorial)
					ArrayList<String> synTokens = new ArrayList<String>();
					getCombiFor(listOfSynonyms, synTokens);

					// for each synonym, check if the reference summary
					// contains it. if at least one matches, then this
					// is a hit
					boolean isMatch = false;

					// now check if the reference summary has any of the
					// synonyms
					for (String syntok : synTokens) {

						if (referenceSummaryTokens.contains(syntok)) {
							referenceSummaryTokens.remove(syntok);
							overlap++;
						}
					}
				}
			}
		} else {
			// WE DONT CARE ABOUT SYNONYMS

			for (String sysTok : systemSummaryTokens) {

				if (referenceSummaryTokens.contains(sysTok)) {
					referenceSummaryTokens.remove(sysTok);
					overlap++;
				}
			}
		}

		if (overlap > 0) {
			double rougeRecall = overlap / (hsrefOriginal.size());
			double rougePrecision = overlap / (systemSummaryTokens.size());
			r.recall = r.recall + rougeRecall;
			r.precision = r.precision + rougePrecision;
		}

		r.count = r.count + 1;

		/*
		 * System.out.println("ROUGE SCORE IS:"); System.out.println(
		 * "ROUGE Recall:"+rougeRecall); System.out.println(
		 * "ROUGE Precision:"+rougePrecision);
		 */
	}

	public void rougeSU(Result r, double overlap, List<String> candSents, List<String> refSents){
		Collection<String> hsrefSkipOriginal = getSkipTokens(refSents);

		Collection<String> referenceSummarySkip = getSkipTokens(refSents);
		Collection<String> systemSummarySkip = getSkipTokens(candSents);

		for (String sysTok : systemSummarySkip) {

			if (referenceSummarySkip.contains(sysTok)) {
				referenceSummarySkip.remove(sysTok);
				overlap++;
			}
		}

		settings.NGRAM = 1;
		Collection<String> hsrefNgramOriginal = getNGramTokens(refSents);

		Collection<String> referenceSummaryTokens = getNGramTokens(refSents);
		Collection<String> systemSummaryTokens = getNGramTokens(candSents);

		for (String sysTok : systemSummaryTokens) {

			if (referenceSummaryTokens.contains(sysTok)) {
				referenceSummaryTokens.remove(sysTok);
				overlap++;
			}
		}

		if(overlap > 0){
			double rougeRecall = overlap / (hsrefSkipOriginal.size() + hsrefNgramOriginal.size());
			double rougePrecision = overlap / (systemSummarySkip.size() + systemSummaryTokens.size());
			r.recall = r.recall + rougeRecall;
			r.precision = r.precision + rougePrecision;
		}

		r.count = r.count + 1;
	}

	public void semiRougeN(Result r, double overlap, List<String> candSents, List<String> refSents){

		Collection<String> referenceSummaryTokens = getNGramTokens(refSents);
		Collection<String> systemSummaryTokens = getNGramTokens(candSents);

		for (String sysTok : systemSummaryTokens) {
			overlap += smatch(sysTok, referenceSummaryTokens);
		}

		if(overlap > 0){
			r.recall = r.recall + (overlap / (referenceSummaryTokens.size()));
			r.precision = r.precision + (overlap / (systemSummaryTokens.size()));
		}

		r.count = r.count + 1;

	}

	public void softRougeN(Result r, double overlap, List<String> candSents, List<String> refSents){

		Collection<String> referenceSummaryTokens = getNGramTokens(refSents);
		Collection<String> systemSummaryTokens = getNGramTokens(candSents);

		for (String sysTok : systemSummaryTokens) {
			overlap += similar(sysTok, referenceSummaryTokens);
		}

		if(overlap > 0){
			r.recall = r.recall + (overlap / (referenceSummaryTokens.size()));
			r.precision = r.precision + (overlap / (systemSummaryTokens.size()));
		}

		r.count = r.count + 1;

	}

	public void semiRougeSU(Result r, double overlap, List<String> candSents, List<String> refSents){

		settings.NGRAM = 2;
		Collection<String> referenceSummarySkip = getSkipTokens(refSents);
		Collection<String> systemSummarySkip = getSkipTokens(candSents);

		for (String sysTok : systemSummarySkip) {
			overlap += smatch(sysTok, referenceSummarySkip);
		}

		settings.NGRAM = 1;
		Collection<String> referenceSummaryTokens = getNGramTokens(refSents);
		Collection<String> systemSummaryTokens = getNGramTokens(candSents);

		for (String sysTok : systemSummaryTokens) {
			overlap += smatch(sysTok, referenceSummaryTokens);
		}
		/*settings.NGRAM = 1;
		Collection<String> hsrefNgramOriginal = getNGramTokens(refSents);

		Collection<String> referenceSummaryTokens = getNGramTokens(refSents);
		Collection<String> systemSummaryTokens = getNGramTokens(candSents);

		for (String sysTok : systemSummaryTokens) {

			if (referenceSummaryTokens.contains(sysTok)) {
				referenceSummaryTokens.remove(sysTok);
				overlap++;
			}
		}*/
		if(overlap > 0){
			double rougeRecall = overlap / (referenceSummarySkip.size() + referenceSummaryTokens.size());
			double rougePrecision = overlap / (systemSummarySkip.size() + systemSummaryTokens.size());
			r.recall = r.recall + rougeRecall;
			r.precision = r.precision + rougePrecision;
		}

		r.count = r.count + 1;
	}

	public void softRougeSU(Result r, double overlap, List<String> candSents, List<String> refSents){

		settings.NGRAM = 2;
		Collection<String> referenceSummarySkip = getSkipTokens(refSents);
		Collection<String> systemSummarySkip = getSkipTokens(candSents);

		for (String sysTok : systemSummarySkip) {
			overlap += smatch(sysTok, referenceSummarySkip);
		}

		settings.NGRAM = 1;
		Collection<String> referenceSummaryTokens = getNGramTokens(refSents);
		Collection<String> systemSummaryTokens = getNGramTokens(candSents);

		for (String sysTok : systemSummaryTokens) {
			overlap += similar(sysTok, referenceSummaryTokens);
		}
		/*settings.NGRAM = 1;
		Collection<String> hsrefNgramOriginal = getNGramTokens(refSents);

		Collection<String> referenceSummaryTokens = getNGramTokens(refSents);
		Collection<String> systemSummaryTokens = getNGramTokens(candSents);

		for (String sysTok : systemSummaryTokens) {

			if (referenceSummaryTokens.contains(sysTok)) {
				referenceSummaryTokens.remove(sysTok);
				overlap++;
			}
		}*/
		if(overlap > 0){
			double rougeRecall = overlap / (referenceSummarySkip.size() + referenceSummaryTokens.size());
			double rougePrecision = overlap / (systemSummarySkip.size() + systemSummaryTokens.size());
			r.recall = r.recall + rougeRecall;
			r.precision = r.precision + rougePrecision;
		}

		r.count = r.count + 1;
	}

	public void rougeL(Result r, double overlap, List<String> candSents, List<String> refSents){
		for(String ri : refSents){
			overlap += lcsUnion(ri, candSents);
		}

		settings.NGRAM = 1;

		Collection<String> referenceSummaryTokens = getNGramTokens(refSents);
		Collection<String> systemSummaryTokens = getNGramTokens(candSents);
		if(overlap > 0){
			double rougeRecall = overlap / (referenceSummaryTokens.size());
			double rougePrecision = overlap / (systemSummaryTokens.size());
			r.recall = r.recall + rougeRecall;
			r.precision = r.precision + rougePrecision;
		}

		r.count = r.count + 1;
	}

	public void softRL(Result r, double overlap, List<String> candSents, List<String> refSents){
		settings.NGRAM = 1;
		for(String ri : refSents){
			overlap += lssUnion(ri, candSents);
		}

		Collection<String> referenceSummaryTokens = getNGramTokens(refSents);
		Collection<String> systemSummaryTokens = getNGramTokens(candSents);
		if(overlap > 0){
			double rougeRecall = overlap / (referenceSummaryTokens.size());
			double rougePrecision = overlap / (systemSummaryTokens.size());
			r.recall = r.recall + rougeRecall;
			r.precision = r.precision + rougePrecision;
		}

		r.count = r.count + 1;

	}


	public List<String> lssDp(String[] x, String[] y){
		int p1 = x.length+1;
		int p2 = y.length+1;
		int[][] b = new int[p1][p2];
		int[][] c = new int[p1][p2];
		WordembeddingUtil wu = WordembeddingUtil.getInstance();
		for(int i=1; i<p1; i++) {
			double[] veci = wu.getAvgVector(x[i-1]);
			if(veci == null){
				for(int j = 1; j < p2; j++){
					c[i][j] = c[i-1][j];
					b[i][j] = 2;
				}
				continue;
			}
			for(int j=1; j<p2; j++) {
				double[] vecj = wu.getAvgVector(y[j-1]);
				if(vecj == null){
					c[i][j] = c[i][j-1];
					b[i][j] = 3;
					continue;
				}
				double sim = cos(veci, vecj);
				if(sim >= settings.ALPHA){
					c[i][j] = c[i-1][j-1] + 1;
					b[i][j] = 1;
				}else if(c[i-1][j] > c[i][j-1]){
					c[i][j] = c[i-1][j];
					b[i][j] = 2;
				}else{
					c[i][j] = c[i][j-1];
					b[i][j] = 3;
				}

			}
		}
		p1 -= 1;
		p2 -= 1;
		List<String> res = new ArrayList<String>();
		while(b[p1][p2] != 0){
			int flag = b[p1][p2];
			if(flag == 1){
				res.add(x[p1-1]);
				p1 -= 1;
				p2 -= 1;
			}else if(flag == 2){
				p1 -=1;
			}else if(flag == 3){
				p2 -=1;
			}
		}
		return res;
	}

	public int lssUnion(String r, List<String> candSents){
		Collection<String> res = new HashSet<String>();
		String[] rArr = r.split(" ");
		for(String c : candSents){
			String[] cArr = c.split(" ");
			res.addAll(lssDp(rArr, cArr));
		}
		return res.size();
	}

	public int lcsUnion(String r, List<String> candSents){
		Collection<String> res = new HashSet<String>();
		String[] rArr = r.split(" ");
		for(String c : candSents){
			String[] cArr = c.split(" ");
			res.addAll(lcsDp(rArr, cArr));
		}
		return res.size();
	}

	public List<String> lcsDp(String[] x, String[] y) {
		int p1 = x.length+1;
		int p2 = y.length+1;
		int[][] b = new int[p1][p2];
		int[][] c = new int[p1][p2];

		for(int i=1; i<p1; i++) {
			for(int j=1; j<p2; j++) {
				if( x[i-1].equals(y[j-1])) {
					c[i][j] = c[i-1][j-1] + 1;
					b[i][j] = 1;
				}else if(c[i-1][j] >= c[i][j-1]) {
					c[i][j] = c[i-1][j];
					b[i][j] = 2;
				}else{
					c[i][j] = c[i][j-1];
					b[i][j] = 3;
				}
			}
		}

		p1 -= 1;
		p2 -= 1;
		List<String> res = new ArrayList<String>();
		while(b[p1][p2] != 0){
			int flag = b[p1][p2];
			if(flag == 1){
				res.add(x[p1-1]);
				p1 -= 1;
				p2 -= 1;
			}else if(flag == 2){
				p1 -=1;
			}else if(flag == 3){
				p2 -=1;
			}
		}
		return res;
	}


	public double smatch(String ngram, Collection<String> referenceSummaryTokens){
		double maxSim = 0;
		double[] cvec = null;
		WordembeddingUtil wu = WordembeddingUtil.getInstance();
		cvec = wu.getAvgVector(ngram);
		if(cvec == null) return 0;
		for (String refTok : referenceSummaryTokens) {
			double[] rvec = wu.getAvgVector(refTok);
			if(rvec == null) continue;
			double sim = cos(cvec, rvec);
			if(maxSim < sim) maxSim = sim;
		}
		if(maxSim >= settings.ALPHA) return 1;
		return 0;
	}

	public double similar(String ngram, Collection<String> referenceSummaryTokens){
		double maxSim = 0;
		double[] cvec = null;
		WordembeddingUtil wu = WordembeddingUtil.getInstance();
		cvec = wu.getAttVector(ngram);
		if(cvec == null) return 0;
		for (String refTok : referenceSummaryTokens) {
			double[] rvec = wu.getAttVector(refTok);
			if(rvec == null) continue;
			double sim = cos(cvec, rvec);
			if(maxSim < sim) maxSim = sim;
		}
		if(maxSim > settings.ALPHA) return maxSim;
		return 0;
	}


	public double cos(double[] a, double[] b){
		double sim = 0;
		double a_2 = 0;
		double b_2 = 0;
		for(int i = 0; i < a.length; i++){
			sim += a[i] * b[i];
			a_2 += a[i] * a[i];
			b_2 += b[i] * b[i];
		}
		return sim/(Math.sqrt(a_2)*Math.sqrt(b_2));
	}
}
