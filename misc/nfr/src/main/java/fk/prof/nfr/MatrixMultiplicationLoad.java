package fk.prof.nfr;

import java.util.Random;

/**
 * Created by gaurav.ashok on 04/04/17.
 */
public class MatrixMultiplicationLoad {

    private Random rnd = new Random();

    int size;
    float[][] matrix1, matrix2;
    float [][] mul;

    public MatrixMultiplicationLoad(int size) {
        this.size = size;

        this.matrix1 = new float[size][size];
        this.matrix2 = new float[size][size];
        this.mul = new float[size][size];
    }

    public void reset() {
        for(int i = 0; i < size; ++i) {
            for(int j = 0; j < size; ++j) {
                matrix1[i][j] = rnd.nextFloat() + 1.0f;
                matrix2[i][j] = rnd.nextFloat() + 1.0f;
            }
        }
    }

    public void multiply() {
        for_all_i();
    }

    private void for_all_i() {
        for(int i = 0; i < size; ++i) {
            for_all_j(i);
        }
    }

    private void for_all_j(int i) {
        for(int j = 0; j < size; ++j) {
            for_i_j(i, j);
        }
    }

    private void for_i_j(int i, int j) {
        float sum = 0.0f;
        for(int k = 0; k < size; ++k) {
            sum += matrix1[i][k] * matrix2[k][j];
        }

        mul[i][j] = sum;
    }
}
