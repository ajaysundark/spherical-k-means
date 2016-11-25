import java.io.*;
import java.util.*;

public class sphkmeans {

    public static void main(String[] args) {
        SKmeansCluster sc = new SKmeansCluster();
        if (args.length > 0)
        {
            try
            {
                sc.performSKmeans(args[0], args[4], args[1], Integer.parseInt(args[2]), Integer.parseInt(args[3]));
            }
            catch (NullPointerException e)
            {
                e.printStackTrace();
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
}

class SKmeansCluster
{
    private Random rand;
    private int mini;
    private int maxi;
    private double best_obj_fun;
    private double obj_fun_now;
    private TreeMap<Integer, List<Integer>> finalClusters;

    public void performSKmeans(String inputFilename, String outputFilename,
                             String classFile, int numClusters, int numTrials) throws IOException
    {
        System.out.println("Loading data..");
        Map<String, Map<String, Double>> input = extractArticles(inputFilename);
        rand = new Random();
        mini = 0;
        maxi = input.size()-1;
        best_obj_fun = 0.0;
        obj_fun_now = 0.0;

        Map<String,Integer> bestResult = null;
        for (int seed=1;seed<(numTrials*2);seed+=2) {
            rand.setSeed(seed);
            System.out.println("Trial #" + (int)(seed+1)/2);
            System.out.println("Begin clustering to find " + numClusters + " clusters... ");
            double stamp = System.currentTimeMillis();
            Map<String,Integer> result = cluster(input, numClusters);
            System.out.println("Spherical Kmeans finished in time " + (System.currentTimeMillis() - stamp) + "\n");
            if (seed==1 || obj_fun_now>=best_obj_fun) {
                bestResult = result;
                best_obj_fun = obj_fun_now;
            }
        }

        System.out.println("Finished");

        System.out.println("Best trial objective function : " + best_obj_fun);
        writeToOut(bestResult, outputFilename);

        computeEntropyPurityMatrix(bestResult, classFile, numClusters);
    }

    private int getRandomPt() {
        return rand.nextInt((maxi - mini) + 1) + mini;
    }

    private Map<String, Map<String, Double>> extractArticles(String filename) throws IOException
    {
        Map<String,Map<String, Double>> allArticles = new HashMap<>();

        BufferedReader br = null;
        String line;

        try
        {
            br = new BufferedReader(new FileReader(filename));
            while ((line = br.readLine()) != null)
            {
                String[] idWordFreq = line.split(",");
                if (allArticles.containsKey(idWordFreq[0]))
                {
                    allArticles.get(idWordFreq[0]).put(idWordFreq[1], Double.parseDouble(idWordFreq[2]));
                }
                else
                {
                    Map<String, Double> newMap = new HashMap<>();
                    newMap.put(idWordFreq[1], Double.parseDouble(idWordFreq[2]));
                    allArticles.put(idWordFreq[0], newMap);
                }
            }
        }
        catch (FileNotFoundException fe)
        {
            fe.printStackTrace();
        }
        finally
        {
            if (br != null)
            {
                try
                {
                    br.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        return allArticles;
    }

    private Map<Integer, Map<String, Double>> getFirstCentroids(Map<String, Map<String, Double>> allInputs, int K)
    {
        Map<Integer, Map<String, Double>> initPts = new TreeMap<>();
        List<Integer> dupList = new ArrayList<>(K);
        List<String> allKeys = new ArrayList<>(allInputs.keySet());
        for (int i = 0; i < K; i++)
        {
            int Ci = getRandomPt();
            while (dupList.contains(Integer.valueOf(Ci))) {
                Ci = getRandomPt();
            }
            dupList.add(Integer.valueOf(Ci));
            Map<String, Double> wordFreq = allInputs.get(allKeys.get(Ci));
            initPts.put(i, wordFreq);
        }
        return initPts;
    }

    private void writeToOut(Map<String, Integer> spkmeansResult, String resultFile) throws IOException
    {
        FileWriter resWriter = new FileWriter(resultFile);
        Set<Map.Entry<String,Integer>> resultSet = spkmeansResult.entrySet();

        for (Map.Entry<String, Integer> entry : resultSet)
        {
            resWriter.append(entry.getKey() + "," + entry.getValue() + "\n");
        }

        resWriter.flush();
        resWriter.close();
    }

    private Map<String, Integer> cluster(Map<String, Map<String, Double>> inputData, int clusterNum)
    {
        int iterations = 0;
        int articleLen = inputData.size();
        String[] articleOriginalId = new String[articleLen]; // for identifying every articles
        List<String> allKeys = new ArrayList<>(inputData.keySet());
        for (int i = 0; i < articleLen; i++)
        {
            articleOriginalId[i] = allKeys.get(i);
        }

        TreeMap<Integer, List<Integer>> clusterOfArticles = new TreeMap<>(); /* a map between cluster number and fake article id */
        Map<Integer, Map<String, Double>> currentCentroids = getFirstCentroids(inputData, clusterNum); /* cluster number vs Pt at the space */
        int[] foundClusters = new int[articleLen];
        double [][] similarity = new double[articleLen][clusterNum]; // similarity of every point from every centroids
        double overallSimilarity = 0.0;
        while (true)
        {
            System.out.println("Now clustering, iteration (" + (iterations++) + ") :");
            for (int articleId = 0; articleId < articleLen; articleId++)
            { // for each article
                for(int clusterId = 0; clusterId < clusterNum; clusterId++)
                { // find cosine similarity against all clusters
                    similarity[articleId][clusterId] = cosineSimilarity(inputData.get(articleOriginalId[articleId]), currentCentroids.get(clusterId));
                }
            }

            int[] closestCentroidId = new int[articleLen]; // for every article get the closest centroid
            for (int articleId = 0; articleId < articleLen; articleId++)
            {
                closestCentroidId[articleId] = getClosestCentroid(similarity, articleId);
                foundClusters[articleId] = closestCentroidId[articleId];
            }

            for (int articleId = 0; articleId < articleLen; articleId++)
            { // for each article
                for(int clusterId = 0; clusterId < clusterNum; clusterId++)
                { // find cosine similarity against all clusters
                    overallSimilarity = similarity[articleId][clusterId];
                }
            }

            if(iterations >= 4) {
                // do this at the end of every trial
                obj_fun_now = overallSimilarity;
                if (finalClusters==null || (obj_fun_now > best_obj_fun)) {
                    finalClusters = clusterOfArticles;
                }

                break;
            }

            clusterOfArticles.clear();
            for(int articleId = 0; articleId < articleLen; articleId++)
            {
                if(clusterOfArticles.containsKey(closestCentroidId[articleId]))
                {
                    clusterOfArticles.get(closestCentroidId[articleId]).add(articleId);
                }
                else
                {
                    List<Integer> articleMembers = new ArrayList<>();
                    articleMembers.add(articleId);
                    clusterOfArticles.put(closestCentroidId[articleId], articleMembers);
                }
            }

            updateCentroid(clusterOfArticles, currentCentroids, inputData, articleOriginalId);
        }

        Map<String, Integer> result = new TreeMap<>();
        for(int i = 0; i < articleLen; i++)
        {
            result.put(articleOriginalId[i], foundClusters[i]);
        }
        return result;
    }

    private int getClosestCentroid(double[][] similarity,int m)
    {
        double absMax = similarity[m][0];

        int idx = 0;
        for(int i = 1; i < similarity[m].length; i++)
        {
            if(similarity[m][i] > absMax)
            {
                absMax = similarity[m][i];
                idx = i;
            }
        }
        return idx;
    }

    private void updateCentroid(Map<Integer, List<Integer>> clusters,
                                Map<Integer, Map<String, Double>> centroids, Map<String, Map<String, Double>> allData, String[] originals)
    {
        for (int clusterId=0; clusterId<clusters.size(); ++clusterId) {
            ArrayList<Integer> articles = (ArrayList<Integer>) clusters.get(clusterId);
            Map<String, Double> centroidDimensions = centroids.get(clusterId);
            centroidDimensions.clear();

            double den = (articles==null) ? 0 : (double) articles.size();
            if (den==0) {
                // do we need to handle empty clusters?
                continue;
            }

            for (Integer articleId : articles) {
                String articleIndex = originals[articleId];
                if (null!=articleIndex) {
                    Map<String, Double> wordFreq = allData.get(articleIndex);
                    if (wordFreq!=null) {
                        for (String word : wordFreq.keySet()) {
                            if (centroidDimensions.containsKey(word)) {
                                centroidDimensions.put(word, centroidDimensions.get(word) + wordFreq.get(word));
                            }
                            else {
                                centroidDimensions.put(word, wordFreq.get(word));
                            }
                        }
                    } else {
                        System.out.println("ERR: wordFreq was null!");
                    }
                } else {
                    System.out.println("ERR: articleIndex was null!");
                }
            }

            for(Map.Entry<String, Double> dimension : centroidDimensions.entrySet()) {
                centroidDimensions.put(dimension.getKey(),
                        (dimension.getValue()/den));
            }
        }
    }

    //compute cosine similarity
    private double cosineSimilarity(Map<String, Double> vector1, Map<String, Double> vector2)
    {
        Set<String> allKeys = new HashSet<>(vector1.keySet());
        allKeys.addAll(vector2.keySet());

        double result = 0;
        double dotProduct = 0;
        double leftNorm = 0;
        double rightNorm = 0;

        double val1 = 0;
        double val2 = 0;
        for(String key : allKeys) {
            val1 = vector1.containsKey(key) ? vector1.get(key) : 0;
            val2 = vector2.containsKey(key) ? vector2.get(key) : 0;
            dotProduct += val1 * val2;
            leftNorm += val1 * val1;
            rightNorm += val2 * val2;
        }
        result = dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        return result ;
    }

    private void computeEntropyPurityMatrix(Map<String, Integer> result, String classFile, int K) throws IOException
    {
        FileReader classReader = new FileReader(classFile);
        BufferedReader classBR = new BufferedReader(classReader);
        Set topicSet = new TreeSet<String>(); // we considered only the 20 frequent topics
        Map<Integer, Map<String, Integer>> clusterVsTopic; /* found cluster ID : <topic, #instances of topic>*/

        if (finalClusters!=null)
            clusterVsTopic = new HashMap<>(finalClusters.size());
        else
            clusterVsTopic = new HashMap<>();

        String line;
        String[] pair;
        Integer equivalentCluster;
        while ((line = classBR.readLine()) != null) {
            pair = line.split(",");

            topicSet.add(pair[1]);
            equivalentCluster = result.get(pair[0]); // The topic has been grouped as this cluster locally

            if (clusterVsTopic.containsKey(equivalentCluster)) {
                Map<String, Integer> topicGraph = clusterVsTopic.get(equivalentCluster);
                int myCount = ( topicGraph.containsKey(pair[1]) ) ? topicGraph.get(pair[1]) : 0;
                topicGraph.put(pair[1], myCount+1);
            } else {
                Map<String, Integer> topicGraph = new HashMap<>();
                topicGraph.put(pair[1], 1);
                clusterVsTopic.put(equivalentCluster, topicGraph);
            }
        }

        System.out.println("");
        System.out.format("%10s", "Cluster ID");
        Iterator<String> treeItr = topicSet.iterator();
        while (treeItr.hasNext()) {
            System.out.format("%10s", treeItr.next());
        }
        System.out.format("%10s", "Purity");
        System.out.format("%10s", "Entropy");
        System.out.println("");

        String topic;
        for (Map.Entry<Integer, Map<String, Integer>> entry : clusterVsTopic.entrySet()) {
            System.out.format("%10d", entry.getKey());
            Iterator<String> treeItr2 = topicSet.iterator();
            double entropy = 0;
            double pij = 0;
            double logpij = 0;
            double purity = 0;
            while (treeItr2.hasNext()) {
                Map<String, Integer> mytopics = entry.getValue();
                topic = treeItr2.next();
                if (mytopics!=null && topic!=null && mytopics.containsKey(topic)) {
                    pij = ( mytopics.get(topic)/(double) finalClusters.get(entry.getKey()).size() );
                    purity = (pij > purity) ? pij : purity;
                    logpij = Math.log10(pij)/Math.log10(2);
                    entropy = entropy + ( (pij==0) ? pij : (pij * (0 - logpij)) );
                    System.out.format("%10d", mytopics.get(topic));
                } else {
                    System.out.format("%10d", 0);
                }
            }
            System.out.format("%10f", purity);
            System.out.format("%10f", entropy);
            System.out.println("");
        }
    }
}

