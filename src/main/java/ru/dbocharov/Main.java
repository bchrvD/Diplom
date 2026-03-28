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

        Map<Double, String> valueInPoint = new HashMap<>();

        Scanner scanner = new Scanner(System.in);

        System.out.println("Введите количество точек разбиения: ");
        int countPoint = scanner.nextInt();
        int n = countPoint + 1;

        System.out.println("Введите функцию правой части от x и t (например: sin(x)*t + x^2) или введите test:");
        scanner.nextLine();
        String inputFunctionXT = scanner.nextLine();

        if (inputFunctionXT.equalsIgnoreCase("test")) {
            inputFunctionXT = "2*"+Math.PI+"*sin(2*"+Math.PI+"*x)*(2*"+ Math.PI+"*cos(2*"+Math.PI+"*t)-sin(2*"+Math.PI+"*t))";
        }

        for (int i = 0; i <= n; i++) {
            valueInPoint.put((double) i / n,
                    inputFunctionXT.replaceAll("(?<![a-zA-Z])t(?![a-zA-Z])", String.valueOf((double) i / n)));
        }

        System.out.println("Введите количество базисных функций omega: ");
        int m = scanner.nextInt();
        scanner.nextLine();

        System.out.println("Введите точное решение u(x,t) (введите test для встроенного, или no если точное решение неизвестно):");
        String exactSolutionInput = scanner.nextLine().trim();

        boolean hasExactSolution = !exactSolutionInput.equalsIgnoreCase("no");
        String exactFunctionXT = "";

        if (hasExactSolution) {
            if (exactSolutionInput.equalsIgnoreCase("test")) {
                exactFunctionXT = "sin(2*"+Math.PI+"*x)*cos(2*"+Math.PI+"*t)";
            } else {
                exactFunctionXT = exactSolutionInput;
            }
        }

        List<String> omegas = new ArrayList<>();
        for (int i = 1; i <= m; i++) {
            omegas.add(omegaGenerator(i));
        }

        double tau = 1d / n;
        double[][] matrix = new double[(n + 1) * m][(n + 1) * m];
        UnivariateIntegrator integrator = new SimpsonIntegrator();

        // Заполнение базовых блоков матрицы
        for (int i = 0; i < m; i++) {
            final int powI = i + 1;
            for (int j = 0; j < m; j++) {
                final int powJ = j + 1;
                UnivariateFunction productFunc = x -> (Math.pow(x, powI) * (x - 1)) * (Math.pow(x, powJ) * (x - 1));
                UnivariateFunction derivativeFunc = x -> ((powI + 1) * Math.pow(x, powI) - powI * Math.pow(x, powI - 1)) *
                        ((powJ + 1) * Math.pow(x, powJ) - powJ * Math.pow(x, powJ - 1));

                double integral1 = integrator.integrate(1000000, productFunc, 0, 1);
                double integral2 = integrator.integrate(1000000, derivativeFunc, 0, 1);

                matrix[i][j] = -1.0 / tau * integral1 + 0.5 * integral2; // Блок A
                matrix[i][j + m] = 1.0 / tau * integral1 + 0.5 * integral2; // Блок B
            }
        }

        // Перенос элементов ("лесенка")
        for (int k = 1; k < n; k++) {
            int shift = k * m;
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < m; j++) {
                    matrix[shift + i][shift + j] = matrix[i][j];
                    if (shift + m + j < matrix[0].length) {
                        matrix[shift + i][shift + m + j] = matrix[i][j + m];
                    }
                }
            }
        }

        // Периодическое условие
        for (int i = 0; i < m; i++) {
            int row = n * m + i;
            matrix[row][i] = 1.0;
            matrix[row][n * m + i] = -1.0;
        }

        // Вывод матрицы
        System.out.println("Матрица:");
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
        double a = 0d;
        double b = 1d;
        for (int k = 1; k <= n; k++) {
            double t_k = (double) k / n;
            double t_k_minus_1 = (double) (k - 1) / n;
            String f_at_tk = valueInPoint.get(t_k);
            String f_at_tk_minus_1 = valueInPoint.get(t_k_minus_1);
            String f_averaged_str = "((" + f_at_tk + ") + (" + f_at_tk_minus_1 + "))/2.0";
            for (int i = 0; i < m; i++) {
                String currentOmega = omegas.get(i);
                Expression expression = new ExpressionBuilder("(" + f_averaged_str + ")*(" + currentOmega + ")")
                        .variable("x").build();
                UnivariateFunction function = x -> {
                    expression.setVariable("x", x);
                    return expression.evaluate();
                };
                rightVector[(k - 1) * m + i] = integrator.integrate(1000000, function, a, b);
            }
        }

        System.out.println("Правый вектор: ");
        System.out.println(Arrays.toString(rightVector));

        RealMatrix coefficients = MatrixUtils.createRealMatrix(matrix);
        RealVector constants = MatrixUtils.createRealVector(rightVector);
        DecompositionSolver solver = new LUDecomposition(coefficients).getSolver();
        RealVector solution = solver.solve(constants);

        // Сборка приближенного решения
        Map<Double, String> solutionOfTask = new HashMap<>();
        for (int i = 0; i <= n; i++) {
            String expr = "";
            for (int k = 0; k < m; k++) {
                if (k > 0) expr += "+";
                expr += "(" + solution.getEntry(i * m + k) + ")*(" + omegas.get(k) + ")";
            }
            solutionOfTask.put((double) i / n, expr);
        }

        // Сборка точного решения (если оно задано)
        Map<Double, String> exactMap = new HashMap<>();
        if (hasExactSolution) {
            for (int i = 0; i <= n; i++) {
                exactMap.put((double) i / n,
                        exactFunctionXT.replaceAll("(?<![a-zA-Z])t(?![a-zA-Z])", String.valueOf((double) i / n)));
            }
        }

        plotExactAndApproxSolutions(solutionOfTask, exactMap, hasExactSolution);
    }

    // Метод отрисовки
    public static void plotExactAndApproxSolutions(Map<Double, String> approxMap, Map<Double, String> exactMap, boolean hasExactSolution) {
        double globalMaxDiff = 0;
        List<Double> sortedKeys = new ArrayList<>(approxMap.keySet());
        Collections.sort(sortedKeys);

        for (Double t : sortedKeys) {
            String approxExprStr = approxMap.get(t);

            XYChart chart = new XYChartBuilder()
                    .width(800).height(600)
                    .title("Решение при t = " + String.format("%.2f", t))
                    .xAxisTitle("x").yAxisTitle("U(x)")
                    .build();

            chart.getStyler().setLegendVisible(true);
            chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);

            Expression approxExpr = new ExpressionBuilder(approxExprStr).variable("x").build();
            Expression exactExpr = null;

            if (hasExactSolution) {
                String exactExprStr = exactMap.get(t);
                exactExpr = new ExpressionBuilder(exactExprStr).variable("x").build();
            }

            int points = 100;
            double[] xData = new double[points];
            double[] yApprox = new double[points];
            double[] yExact = new double[points];
            double maxDiff = 0;

            for (int i = 0; i < points; i++) {
                double x = i / (double) (points - 1);
                xData[i] = x;

                // Вычисляем приближенное
                double approxVal = approxExpr.setVariable("x", x).evaluate();
                yApprox[i] = approxVal;

                // Вычисляем точное, только если оно есть
                if (hasExactSolution) {
                    double exactVal = exactExpr.setVariable("x", x).evaluate();
                    yExact[i] = exactVal;
                    maxDiff = Math.max(maxDiff, Math.abs(approxVal - exactVal));
                }
            }

            XYSeries approxSeries = chart.addSeries("Приближенное решение", xData, yApprox);
            approxSeries.setMarker(SeriesMarkers.NONE);
            approxSeries.setLineColor(Color.BLACK);
            approxSeries.setLineStyle(SeriesLines.SOLID);

            if (hasExactSolution) {
                globalMaxDiff = Math.max(globalMaxDiff, maxDiff);

                XYSeries exactSeries = chart.addSeries("Точное решение", xData, yExact);
                exactSeries.setMarker(SeriesMarkers.NONE);
                exactSeries.setLineColor(Color.BLACK);
                exactSeries.setLineStyle(SeriesLines.DASH_DASH);

                System.out.printf("Максимальная разница для t = %.2f: %.6f\n", t, maxDiff);
            }

            new SwingWrapper<>(chart).displayChart();
        }

        if (hasExactSolution) {
            System.out.printf("\nГлобальная максимальная разница: %.6f\n", globalMaxDiff);
        } else {
            System.out.println("\nТочное решение не задано, погрешности не вычислялись.");
        }
    }

    private static String omegaGenerator(int i){
        return "x^%s*(x-1)".formatted(i);
    }
}