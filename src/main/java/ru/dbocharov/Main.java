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
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        Map<Double, String> valueInPoint = new HashMap<>(); //значения f в TOKAK x

        Scanner scanner = new Scanner(System.in);

        System.out.println("Введите количество точек разбиения: ");
        int countPoint = scanner.nextInt();

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

        String omega1 = "x*(x-1)";
        String omega2 = "x^2*(x-1)";

        double tau = 1d / (countPoint + 1);

        double[][] matrix = new double[8][8];

        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix.length; j++) {
                matrix[i][j] = 0;
            }
        }

        double el00 = -1d / tau * (1d / 30) + 0.5 * (1d / 3);
        double el01 = -1d / tau * (1d / 60) + 0.5 * (1d / 6);
        double el02 = 1d / tau * (1d / 30) + 0.5 * (1d / 3);
        double el03 = 1 / tau * (1d / 60) + 0.5 * (1d / 6);
        double el10 = -1d/tau * (1d/60) + 0.5*(1d/6);
        double el11 = -1d / tau * (1d / 105) + 0.5 * (2d / 15);
        double el12 = 1d / tau * (1d / 60) + 0.5 * (1d / 6);
        double el13 = 1d / tau * (1d / 105) + 0.5 * (2d / 15);

        matrix[0][0] = matrix[2][2] = matrix[4][4] = el00;
        matrix[0][1] = matrix[2][3] = matrix[4][5] = el01;
        matrix[0][2] = matrix[2][4] = matrix[4][6] = el02;
        matrix[0][3] = matrix[2][5] = matrix[4][7] = el03;
        matrix[1][0] = matrix[3][2] = matrix[5][4] = el10;
        matrix[1][1] = matrix[3][3] = matrix[5][5] = el11;
        matrix[1][2] = matrix[3][4] = matrix[5][6] = el12;
        matrix[1][3] = matrix[3][5] = matrix[5][7] = el13;
        matrix[6][0] = matrix[7][1] = 1;
        matrix[6][6] = matrix[7][7] = -1;

        //Заполнение правого вектора
        double[] rightVector = new double[8];
        UnivariateIntegrator integrator = new SimpsonIntegrator();
        double a = 0d; // Нижний предел интегрирования
        double b = 1d; // Верхний предел интегрирования

        for (int k = 1; k <= countPoint + 1; k++) {
            // Определяем моменты времени t_k и t_{k-1}
            double t_k = (double) k / (double) (countPoint + 1);
            double t_k_minus_1 = (double) (k - 1) / (double) (countPoint + 1);
            String f_at_tk = valueInPoint.get(t_k);
            String f_at_tk_minus_1 = valueInPoint.get(t_k_minus_1);

            // Создаем строку для усредненной функции: (f(t_k) + f(t_{k-1})) / 2
            String f_averaged_str = "((" + f_at_tk + ") + (" + f_at_tk_minus_1 + "))/2.0";

            Expression expression1 = new ExpressionBuilder("(" + f_averaged_str + ")*(" + omega1 + ")")
                    .variable("x")
                    .build();
            UnivariateFunction function1 = x -> {
                expression1.setVariable("x", x);
                return expression1.evaluate();
            };
            double integral1 = integrator.integrate(1000, function1, a, b);

            Expression expression2 = new ExpressionBuilder("(" + f_averaged_str + ")*(" + omega2 + ")")
                    .variable("x")
                    .build();
            UnivariateFunction function2 = x -> {
                expression2.setVariable("x", x);
                return expression2.evaluate();
            };
            double integral2 = integrator.integrate(1000, function2, a, b);

            // Заполняем вектор правой части по формулам b_{2k-2} и b_{2k-1}
            rightVector[2 * (k - 1)] = integral1;
            rightVector[2 * (k - 1) + 1] = integral2;
        }

        System.out.println();

        // Создаем объекты матрицы и вектора
        RealMatrix coefficients = MatrixUtils.createRealMatrix(matrix);
        RealVector constants = MatrixUtils.createRealVector(rightVector);

        //создаем решатель и решаем суу
        DecompositionSolver solver = new LUDecomposition(coefficients).getSolver();
        RealVector solution = solver.solve(constants);

        Map<Double, String> solutionOfTask = new HashMap<>(); //Значения U(h k)
        int j = 0;
        for (int i = 0; i <= countPoint + 1; i++) {
            solutionOfTask.put((double) i / (double) (countPoint + 1),
                    "(" + solution.getEntry(j++) + ")*(" + omega1 + ")+(" +
                            solution.getEntry(j++) + ")*(" + omega2 + ")");
        }

        // System.out.println("Right Vector: "+ Arrays.toString(rightVector));

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

            // Исправлено: правильные названия методов
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

            // Добавление серий с настройкой цвета и стиля линии
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
}