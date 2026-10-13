package org.auto.comp1945_project.client;

import java.util.*;

public class HMM {

    private final int numStates;
    private final int numObservations;

    // A[i][j] = P(state j | state i)
    private double[][] A;

    // B[i][k] = P(observation k | state i)
    private double[][] B;

    // pi[i] = P(state i at t=0)
    private double[] pi;

    private final Random random = new Random(42);

    public HMM(int numStates, int numObservations) {
        this.numStates = numStates;
        this.numObservations = numObservations;

        A = new double[numStates][numStates];
        B = new double[numStates][numObservations];
        pi = new double[numStates];

        randomInit();
    }

    // ==================== 初始化 ====================

    private void randomInit() {
        for (int i = 0; i < numStates; i++) {
            double sum = 0;
            for (int j = 0; j < numStates; j++) {
                A[i][j] = random.nextDouble();
                sum += A[i][j];
            }
            for (int j = 0; j < numStates; j++) A[i][j] /= sum;
        }

        for (int i = 0; i < numStates; i++) {
            double sum = 0;
            for (int k = 0; k < numObservations; k++) {
                B[i][k] = random.nextDouble();
                sum += B[i][k];
            }
            for (int k = 0; k < numObservations; k++) B[i][k] /= sum;
        }

        double sum = 0;
        for (int i = 0; i < numStates; i++) {
            pi[i] = random.nextDouble();
            sum += pi[i];
        }
        for (int i = 0; i < numStates; i++) pi[i] /= sum;
    }

    // ==================== Baum-Welch ====================

    public void train(List<int[]> sequences, int maxIter, double tolerance) {
        double oldLogLikelihood = Double.NEGATIVE_INFINITY;

        for (int iter = 0; iter < maxIter; iter++) {
            double[][] newA = new double[numStates][numStates];
            double[][] newB = new double[numStates][numObservations];
            double[] newPi = new double[numStates];

            double logLikelihood = 0;

            for (int[] obs : sequences) {
                int T = obs.length;

                double[][] alpha = forward(obs);
                double[][] beta = backward(obs);

                double[] scale = new double[T];
                for (int t = 0; t < T; t++) {
                    double s = 0;
                    for (int i = 0; i < numStates; i++) s += alpha[t][i];
                    scale[t] = s;
                }

                double logProb = 0;
                for (int t = 0; t < T; t++) logProb += Math.log(scale[t]);
                logLikelihood += logProb;

                // gamma
                double[][] gamma = new double[T][numStates];
                for (int t = 0; t < T; t++) {
                    double sum = 0;
                    for (int i = 0; i < numStates; i++) {
                        gamma[t][i] = alpha[t][i] * beta[t][i];
                        sum += gamma[t][i];
                    }
                    for (int i = 0; i < numStates; i++) gamma[t][i] /= sum;
                }

                // xi
                double[][][] xi = new double[T - 1][numStates][numStates];
                for (int t = 0; t < T - 1; t++) {
                    double sum = 0;
                    for (int i = 0; i < numStates; i++) {
                        for (int j = 0; j < numStates; j++) {
                            xi[t][i][j] = alpha[t][i] * A[i][j] * B[j][obs[t + 1]] * beta[t + 1][j];
                            sum += xi[t][i][j];
                        }
                    }
                    for (int i = 0; i < numStates; i++)
                        for (int j = 0; j < numStates; j++)
                            xi[t][i][j] /= sum;
                }

                // accumulate
                for (int i = 0; i < numStates; i++) {
                    newPi[i] += gamma[0][i];
                }

                for (int i = 0; i < numStates; i++) {
                    for (int j = 0; j < numStates; j++) {
                        double sum = 0;
                        for (int t = 0; t < T - 1; t++) sum += xi[t][i][j];
                        newA[i][j] += sum;
                    }
                }

                for (int i = 0; i < numStates; i++) {
                    for (int t = 0; t < T; t++) {
                        newB[i][obs[t]] += gamma[t][i];
                    }
                }
            }

            // normalize
            double piSum = 0;
            for (int i = 0; i < numStates; i++) piSum += newPi[i];
            for (int i = 0; i < numStates; i++) pi[i] = newPi[i] / piSum;

            for (int i = 0; i < numStates; i++) {
                double aSum = 0;
                for (int j = 0; j < numStates; j++) aSum += newA[i][j];
                for (int j = 0; j < numStates; j++) A[i][j] = newA[i][j] / aSum;
            }

            for (int i = 0; i < numStates; i++) {
                double bSum = 0;
                for (int k = 0; k < numObservations; k++) bSum += newB[i][k];
                for (int k = 0; k < numObservations; k++) B[i][k] = newB[i][k] / bSum;
            }

            if (Math.abs(logLikelihood - oldLogLikelihood) < tolerance) {
                System.out.println("[HMM] 收斂於 iter " + iter);
                break;
            }
            oldLogLikelihood = logLikelihood;
        }
    }

    // ==================== Forward ====================

    public double[][] forward(int[] obs) {
        int T = obs.length;
        double[][] alpha = new double[T][numStates];

        for (int i = 0; i < numStates; i++) {
            alpha[0][i] = pi[i] * B[i][obs[0]];
        }

        for (int t = 1; t < T; t++) {
            for (int j = 0; j < numStates; j++) {
                double sum = 0;
                for (int i = 0; i < numStates; i++) {
                    sum += alpha[t - 1][i] * A[i][j];
                }
                alpha[t][j] = sum * B[j][obs[t]];
            }
        }

        return alpha;
    }

    // ==================== Backward ====================

    public double[][] backward(int[] obs) {
        int T = obs.length;
        double[][] beta = new double[T][numStates];

        for (int i = 0; i < numStates; i++) beta[T - 1][i] = 1.0;

        for (int t = T - 2; t >= 0; t--) {
            for (int i = 0; i < numStates; i++) {
                double sum = 0;
                for (int j = 0; j < numStates; j++) {
                    sum += A[i][j] * B[j][obs[t + 1]] * beta[t + 1][j];
                }
                beta[t][i] = sum;
            }
        }

        return beta;
    }

    // ==================== Viterbi ====================

    public int[] viterbi(int[] obs) {
        int T = obs.length;
        double[][] delta = new double[T][numStates];
        int[][] psi = new int[T][numStates];

        for (int i = 0; i < numStates; i++) {
            delta[0][i] = Math.log(pi[i]) + Math.log(B[i][obs[0]]);
        }

        for (int t = 1; t < T; t++) {
            for (int j = 0; j < numStates; j++) {
                double max = Double.NEGATIVE_INFINITY;
                int argmax = 0;
                for (int i = 0; i < numStates; i++) {
                    double v = delta[t - 1][i] + Math.log(A[i][j]);
                    if (v > max) {
                        max = v;
                        argmax = i;
                    }
                }
                delta[t][j] = max + Math.log(B[j][obs[t]]);
                psi[t][j] = argmax;
            }
        }

        int[] path = new int[T];
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < numStates; i++) {
            if (delta[T - 1][i] > max) {
                max = delta[T - 1][i];
                path[T - 1] = i;
            }
        }
        for (int t = T - 2; t >= 0; t--) {
            path[t] = psi[t + 1][path[t + 1]];
        }
        return path;
    }

    // ==================== 取得當前狀態機率 ====================

    public double[] currentStateProbs(int[] obs) {
        double[][] alpha = forward(obs);
        double[][] beta = backward(obs);
        int T = obs.length;

        double[] gamma = new double[numStates];
        double sum = 0;
        for (int i = 0; i < numStates; i++) {
            gamma[i] = alpha[T - 1][i] * beta[T - 1][i];
            sum += gamma[i];
        }
        for (int i = 0; i < numStates; i++) gamma[i] /= sum;
        return gamma;
    }

    // ==================== Getter ====================

    public double[][] getA() { return A; }
    public double[][] getB() { return B; }
    public double[] getPi() { return pi; }
}