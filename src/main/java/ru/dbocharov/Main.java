package ru.dbocharov;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.integration.SimpsonIntegrator;
import org.apache.commons.math3.analysis.integration.UnivariateIntegrator;
import org.apache.commons.math3.linear.*;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.markers.SeriesMarkers;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        Map<Double, String> valueInPoint = new HashMap<>(); //значения f в TOKAK x

        Scanner scanner = new Scanner(System.in);

        System.out.println("Введите количество точек разбиения: ");
        int countPoint = scanner.nextInt();
        int n = countPoint + 1; // количество интервалов
        System.out.println("Введите функцию от x и t (например: sin(x)*t + x^2):");
        scanner.nextLine();
        String inputFunctionXT = scanner.nextLine();

        if (inputFunctionXT.equals("test")) {
            inputFunctionXT = "2*"+Math.PI+"*sin(2*"+Math.PI+"*x)*(2*"+ Math.PI+"*cos(2*"+Math.PI+"*t)-sin(2*"+Math.PI+"*t))";
        }

        for (int i = 0; i <= countPoint + 1; i++) {
            valueInPoint.put((double) i / (double) (countPoint + 1),
                    inputFunctionXT.replaceAll("t",
                            String.valueOf((double) i / (double)
                                    (countPoint + 1))));
        }
        System.out.println("Введите количество базисных функций omega: ");
        int m = scanner.nextInt(); //количество омег
        List<String> omegas = new ArrayList<>();
        for (int i = 1; i<=m;i++){
            omegas.add(omegaGenerator(i));
        }
        String omega1 = "x*(x-1)";
        String omega2 = "x^2*(x-1)";

        double tau = 1d / (countPoint + 1);

        double[][] matrix = new double[(n+1)*m][m*(n+1)];

        UnivariateIntegrator integrator = new SimpsonIntegrator();
        //Заполннение матрицы
        for (int i = 0; i < m; i++) {
            final int powI = i + 1;
            for (int j = 0; j < m; j++) {
                final int powJ = j + 1;
                UnivariateFunction productFunc = x -> {
                    double omegaI = Math.pow(x, powI) * (x - 1);
                    double omegaJ = Math.pow(x, powJ) * (x - 1);
                    return omegaI * omegaJ;
                };
                UnivariateFunction derivativeFunc = x -> {
                    double dOmegaI = (powI + 1) * Math.pow(x, powI) - powI * Math.pow(x, powI - 1);
                    double dOmegaJ = (powJ + 1) * Math.pow(x, powJ) - powJ * Math.pow(x, powJ - 1);
                    return dOmegaI * dOmegaJ;
                };
                double integral1 = integrator.integrate(1000, productFunc, 0, 1);
                double integral2 = integrator.integrate(1000, derivativeFunc, 0, 1);
                matrix[i][j] = -1.0 / tau * integral1 + 0.5 * integral2;
            }
        }

        for (int i = 0; i < m; i++) {
            final int powI = i + 1;
            for (int j = m; j < 2 * m; j++) {
                final int powJ = j + 1 - m;
                UnivariateFunction productFunc = x -> {
                    double omegaI = Math.pow(x, powI) * (x - 1);
                    double omegaJ = Math.pow(x, powJ) * (x - 1);
                    return omegaI * omegaJ;
                };
                UnivariateFunction derivativeFunc = x -> {
                    double dOmegaI = (powI + 1) * Math.pow(x, powI) - powI * Math.pow(x, powI - 1);
                    double dOmegaJ = (powJ + 1) * Math.pow(x, powJ) - powJ * Math.pow(x, powJ - 1);
                    return dOmegaI * dOmegaJ;
                };
                double integral1 = integrator.integrate(1000, productFunc, 0, 1);
                double integral2 = integrator.integrate(1000, derivativeFunc, 0, 1);
                matrix[i][j] = 1.0 / tau * integral1 + 0.5 * integral2;
            }
        }
        // Переносим элементы на нужные места (создаем лесенку)
        for (int k = 1; k < n; k++) {
            int shift = k * m;
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < m; j++) {
                    // Копируем блок A
                    matrix[shift + i][shift + j] = matrix[i][j];
                    // Копируем блок B
                    if (shift + m + j < matrix[0].length) {
                        matrix[shift + i][shift + m + j] = matrix[i][j + m];
                    }
                }
            }
        }
        // Заполнение периодического условия
        for (int i = 0; i < m; i++) {
            int row = n * m + i;
            matrix[row][i] = 1.0;
            matrix[row][n * m + i] = -1.0;
        }

        System.out.println("МАТРИЦА:");
        int rows = matrix.length;
        int cols = matrix[0].length;

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                System.out.printf("%10.4f ", matrix[i][j]);
            }
            System.out.println();
        }


        // Заполнение правого вектора
        double[] rightVector = new double[(n + 1) * m];
        double a = 0d; // Нижний предел интегрирования
        double b = 1d; // Верхний предел интегрирования
        for (int k = 1; k <= n; k++) {
            double t_k = (double) k / n;
            double t_k_minus_1 = (double) (k - 1) / n;
            String f_at_tk = valueInPoint.get(t_k);
            String f_at_tk_minus_1 = valueInPoint.get(t_k_minus_1);
            String f_averaged_str = "((" + f_at_tk + ") + (" + f_at_tk_minus_1 + "))/2.0";
            for (int i = 0; i < m; i++) {
                String currentOmega = omegas.get(i);
                Expression expression = new ExpressionBuilder("(" + f_averaged_str + ")*(" + currentOmega + ")")
                        .variable("x")
                        .build();
                UnivariateFunction function = x -> {
                    expression.setVariable("x", x);
                    return expression.evaluate();
                };
                double integral = integrator.integrate(1000, function, a, b);
                rightVector[(k - 1) * m + i] = integral;
            }
        }
        System.out.println("ПРАВЫЙ ВЕКТОР: ");
        System.out.println(Arrays.toString(rightVector));

        // Создаем объекты матрицы и вектора
        RealMatrix coefficients = MatrixUtils.createRealMatrix(matrix);
        RealVector constants = MatrixUtils.createRealVector(rightVector);

        //создаем решатель и решаем слу
        DecompositionSolver solver = new LUDecomposition(coefficients).getSolver();
        RealVector solution = solver.solve(constants);

        Map<Double, String> solutionOfTask = new HashMap<>(); //Значения U(h k)
        int j = 0;
        for (int i = 0; i <= countPoint + 1; i++) {
            solutionOfTask.put((double) i / (double) (countPoint + 1),
                    "(" + solution.getEntry(j++) + ")*(" + omega1 + ")+(" +
                            solution.getEntry(j++) + ")*(" + omega2 + ")");
        }

        Map<Double, String> currentMap = new HashMap<>(); //Точное решение
        String currentFunctionXT = "sin(2*"+Math.PI+"*x)*cos(2*"+Math.PI+"*t)";
        for (int i = 0; i <= countPoint + 1; i++) {
            currentMap.put((double) i / (double) (countPoint + 1),
                    currentFunctionXT.replaceAll("t",
                            String.valueOf((double) i / (double)
                                    (countPoint + 1))));
        }

        plotExactAndApproxSolutions(solutionOfTask, currentMap);
    }

    public static void plotExactAndApproxSolutions(Map<Double, String> approxMap, Map<Double, String> exactMap) {
        double globalMaxDiff = 0;
        for (Double t : approxMap.keySet()) {
            String approxExprStr = approxMap.get(t);
            String exactExprStr = exactMap.get(t);

            XYChart chart = new XYChartBuilder()
                    .width(800).height(600)
                    .title("Решение при t = " + String.format("%.2f", t))
                    .xAxisTitle("x").yAxisTitle("U(x)")
                    .build();

            chart.getStyler().setLegendVisible(true);
            chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);

            Expression approxExpr;
            Expression exactExpr;
            try {
                approxExpr = new ExpressionBuilder(approxExprStr).variable("x").build();
                exactExpr = new ExpressionBuilder(exactExprStr).variable("x").build();
            } catch (Exception e) {
                System.out.println("Ошибка при разборе выражения при t = " + t);
                continue;
            }

            int points = 100;
            double[] xData = new double[points];
            double[] yApprox = new double[points];
            double[] yExact = new double[points];
            double maxDiff = 0;

            for (int i = 0; i < points; i++) {
                double x = i / (double) (points - 1);
                xData[i] = x;
                approxExpr.setVariable("x", x);
                exactExpr.setVariable("x", x);
                double approxVal = approxExpr.evaluate();
                double exactVal = exactExpr.evaluate();
                yApprox[i] = approxVal;
                yExact[i] = exactVal;
                double diff = Math.abs(approxVal - exactVal);
                if (diff > maxDiff) {
                    maxDiff = diff;
                }
            }
            globalMaxDiff = Math.max(globalMaxDiff, maxDiff);

            XYSeries approxSeries = chart.addSeries("Приближенное решение", xData, yApprox);
            approxSeries.setMarker(SeriesMarkers.NONE);
            approxSeries.setLineColor(Color.BLACK);
            approxSeries.setLineStyle(SeriesLines.SOLID);

            XYSeries exactSeries = chart.addSeries("Точное решение", xData, yExact);
            exactSeries.setMarker(SeriesMarkers.NONE);
            exactSeries.setLineColor(Color.BLACK);
            exactSeries.setLineStyle(SeriesLines.DASH_DASH);

            new SwingWrapper<>(chart).displayChart();

            System.out.printf("Максимальная разница для t = %.2f: %.6f\n", t, maxDiff);
        }
        System.out.printf("Глобальная максимальная разница: %.6f\n", globalMaxDiff);
    }

    private static String omegaGenerator(int i){
        return "x^%s*(x-1)".formatted(i);
    }
}