package org.auto.comp1945_project.client;

import java.util.*;

public class SmileBiomeTrainer {

    private static double[][] W;   // 3 × 6 權重
    private static double[] b;     // 3 偏差
    private static final int NUM_ORES = 6;
    private static final int NUM_BIOMES = 3;
    private static final int WINDOW = 20;

    public static void buildModel() { }

    // ==================== 訓練 ====================

    public static void train(List<int[]> sequences) {
        if (sequences == null || sequences.isEmpty()) {
            System.out.println("[LR] 沒有訓練資料");
            return;
        }

        // ---------- 1. 準備資料 ----------
        List<double[]> featList = new ArrayList<>();
        List<Integer> labelList = new ArrayList<>();

        for (int[] seq : sequences) {
            if (seq == null || seq.length == 0) continue;
            for (int t = 0; t < seq.length; t++) {
                double[] feat = new double[NUM_ORES];
                int from = Math.max(0, t - WINDOW + 1);
                for (int i = from; i <= t; i++) {
                    if (seq[i] >= 0 && seq[i] < NUM_ORES) feat[seq[i]]++;
                }
                featList.add(feat);
                labelList.add(seq[t] / 2);
            }
        }

        int n = featList.size();
        if (n == 0) {
            System.out.println("[LR] 沒有有效樣本");
            return;
        }

        // ---------- 2. 檢查類別數 ----------
        Set<Integer> uniqueLabels = new HashSet<>(labelList);
        if (uniqueLabels.size() < 2) {
            System.out.println("[LR] 訓練資料只有 " + uniqueLabels.size()
                    + " 個類別（" + uniqueLabels + "），跳過訓練");
            return;
        }

        System.out.println("[LR] 訓練資料類別分布: " + uniqueLabels
                + "，樣本數: " + n);

        // ---------- 3. 初始化權重 ----------
        W = new double[NUM_BIOMES][NUM_ORES];
        b = new double[NUM_BIOMES];
        Random rng = new Random(42);
        for (int k = 0; k < NUM_BIOMES; k++)
            for (int j = 0; j < NUM_ORES; j++)
                W[k][j] = (rng.nextDouble() - 0.5) * 0.01;

        // ---------- 4. 梯度下降 ----------
        double lr = 0.05;
        int epochs = 300;

        for (int epoch = 0; epoch < epochs; epoch++) {
            double[][] gradW = new double[NUM_BIOMES][NUM_ORES];
            double[] gradB = new double[NUM_BIOMES];

            for (int i = 0; i < n; i++) {
                double[] feat = featList.get(i);
                int label = labelList.get(i);

                // softmax 前向
                double[] probs = softmax(feat);

                // 梯度
                for (int k = 0; k < NUM_BIOMES; k++) {
                    double diff = probs[k] - (k == label ? 1 : 0);
                    for (int j = 0; j < NUM_ORES; j++)
                        gradW[k][j] += diff * feat[j];
                    gradB[k] += diff;
                }
            }

            // 更新
            for (int k = 0; k < NUM_BIOMES; k++) {
                for (int j = 0; j < NUM_ORES; j++)
                    W[k][j] -= lr * gradW[k][j] / n;
                b[k] -= lr * gradB[k] / n;
            }
        }

        System.out.println("[LR] 訓練完成，樣本: " + n
                + "，類別: " + uniqueLabels.size());
    }

    // ==================== 預測 ====================

    public static Map<String, Double> predict(int[] seq) {
        Map<String, Double> result = new HashMap<>();
        result.put("project:red", 0.0);
        result.put("project:blue", 0.0);
        result.put("project:yellow", 0.0);

        if (W == null || seq == null || seq.length == 0) {
            return result;
        }

        // 特徵
        double[] feat = new double[NUM_ORES];
        int from = Math.max(0, seq.length - WINDOW);
        for (int i = from; i < seq.length; i++) {
            if (seq[i] >= 0 && seq[i] < NUM_ORES) feat[seq[i]]++;
        }

        double[] probs = softmax(feat);

        result.put("project:red", probs[0]);
        result.put("project:blue", probs[1]);
        result.put("project:yellow", probs[2]);
        return result;
    }

    // ==================== softmax ====================

    private static double[] softmax(double[] feat) {
        double[] logits = new double[NUM_BIOMES];
        for (int k = 0; k < NUM_BIOMES; k++) {
            logits[k] = b[k];
            for (int j = 0; j < NUM_ORES; j++)
                logits[k] += W[k][j] * feat[j];
        }

        double maxLogit = logits[0];
        for (int k = 1; k < NUM_BIOMES; k++)
            if (logits[k] > maxLogit) maxLogit = logits[k];

        double sumExp = 0;
        double[] probs = new double[NUM_BIOMES];
        for (int k = 0; k < NUM_BIOMES; k++) {
            probs[k] = Math.exp(logits[k] - maxLogit);
            sumExp += probs[k];
        }
        for (int k = 0; k < NUM_BIOMES; k++) probs[k] /= sumExp;

        return probs;
    }

    // ==================== 狀態查詢 ====================

    public static boolean isReady() { return W != null; }

    public static void clear() { W = null; b = null; }
}