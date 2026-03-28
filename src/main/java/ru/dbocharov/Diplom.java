package ru.dbocharov;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.integration.SimpsonIntegrator;
import org.apache.commons.math3.analysis.integration.UnivariateIntegrator;
import org.apache.commons.math3.linear.*;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.markers.SeriesMarkers;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.List;

public class Diplom extends JFrame {

    private JTextField tfPoints, tfM, tfRightFunc, tfExactFunc;
    private JTextArea logArea;
    private JButton btnCalculate;

    private JPanel chartContainer;
    private JSlider timeSlider;
    private JLabel timeLabel;

    private List<XYChart> generatedCharts;
    private List<Double> generatedTimes;

    public Diplom() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        setTitle("Проекционно-разностный метод (Кранк-Николсон)");
        setSize(1100, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(400);

        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel inputPanel = new JPanel(new GridLayout(8, 1, 5, 5));
        inputPanel.add(new JLabel("Количество точек разбиения по времени (n):"));
        tfPoints = new JTextField("");
        inputPanel.add(tfPoints);

        inputPanel.add(new JLabel("Количество базисных функций (m):"));
        tfM = new JTextField("");
        inputPanel.add(tfM);

        inputPanel.add(new JLabel("Функция f(x,t) [или test]:"));
        tfRightFunc = new JTextField("");
        inputPanel.add(tfRightFunc);

        inputPanel.add(new JLabel("Точное решение u(x,t) [test, no или формула]:"));
        tfExactFunc = new JTextField("");
        inputPanel.add(tfExactFunc);

        JPanel btnPanel = new JPanel(new BorderLayout());
        btnCalculate = new JButton("Вычислить");
        btnCalculate.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnCalculate.setPreferredSize(new Dimension(0, 40));
        btnPanel.add(btnCalculate, BorderLayout.CENTER);
        btnPanel.setBorder(new EmptyBorder(10, 0, 10, 0));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Консоль:"));

        JPanel topSide = new JPanel(new BorderLayout());
        topSide.add(inputPanel, BorderLayout.NORTH);
        topSide.add(btnPanel, BorderLayout.SOUTH);

        leftPanel.add(topSide, BorderLayout.NORTH);
        leftPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        chartContainer = new JPanel(new BorderLayout());
        chartContainer.setBackground(Color.WHITE);
        chartContainer.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        JLabel lblPlaceholder = new JLabel("Здесь появятся графики после расчетов...", SwingConstants.CENTER);
        lblPlaceholder.setFont(new Font("SansSerif", Font.ITALIC, 14));
        chartContainer.add(lblPlaceholder, BorderLayout.CENTER);

        JPanel sliderPanel = new JPanel(new BorderLayout(5, 5));
        timeLabel = new JLabel("Время t = 0.00", SwingConstants.CENTER);
        timeLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        timeSlider = new JSlider(0, 0, 0);
        timeSlider.setEnabled(false);
        timeSlider.setMajorTickSpacing(1);
        timeSlider.setPaintTicks(true);

        timeSlider.addChangeListener(e -> {
            if (generatedCharts != null && !generatedCharts.isEmpty()) {
                int index = timeSlider.getValue();
                displayChartAtIndex(index);
            }
        });

        sliderPanel.add(timeLabel, BorderLayout.NORTH);
        sliderPanel.add(timeSlider, BorderLayout.CENTER);

        rightPanel.add(chartContainer, BorderLayout.CENTER);
        rightPanel.add(sliderPanel, BorderLayout.SOUTH);

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);
        add(splitPane);

        redirectSystemOut();

        btnCalculate.addActionListener(e -> startCalculation());
    }

    private void displayChartAtIndex(int index) {
        chartContainer.removeAll();
        XChartPanel<XYChart> chartPanel = new XChartPanel<>(generatedCharts.get(index));
        chartContainer.add(chartPanel, BorderLayout.CENTER);

        double t = generatedTimes.get(index);
        timeLabel.setText(String.format("Шаг %d из %d | Время t = %.2f", index, generatedTimes.size() - 1, t));

        chartContainer.revalidate();
        chartContainer.repaint();
    }

    private void startCalculation() {
        logArea.setText("");
        btnCalculate.setEnabled(false);
        btnCalculate.setText("Вычисления...");
        timeSlider.setEnabled(false);

        int countPoint, m;
        String rightFuncText = tfRightFunc.getText().trim();
        String exactFuncText = tfExactFunc.getText().trim();

        try {
            countPoint = Integer.parseInt(tfPoints.getText().trim());
            m = Integer.parseInt(tfM.getText().trim());
        } catch (NumberFormatException ex) {
            System.out.println("Ошибка: Поля n и m должны быть целыми числами!");
            resetButton();
            return;
        }

        new Thread(() -> {
            try {
                runMathLogic(countPoint, m, rightFuncText, exactFuncText);
            } catch (Exception ex) {
                System.out.println("\n--- ПРОИЗОШЛА ОШИБКА ---");
                ex.printStackTrace(System.out);
            } finally {
                resetButton();
            }
        }).start();
    }

    private void resetButton() {
        SwingUtilities.invokeLater(() -> {
            btnCalculate.setEnabled(true);
            btnCalculate.setText("Вычислить");
        });
    }

    private void runMathLogic(int countPoint, int m, String inputFunctionXT, String exactSolutionInput) {
        int n = countPoint + 1;
        Map<Double, String> valueInPoint = new HashMap<>();

        if (inputFunctionXT.equalsIgnoreCase("test")) {
            inputFunctionXT = "2*" + Math.PI + "*sin(2*" + Math.PI + "*x)*(2*" + Math.PI + "*cos(2*" + Math.PI + "*t)-sin(2*" + Math.PI + "*t))";
        }

        for (int i = 0; i <= n; i++) {
            valueInPoint.put((double) i / n, inputFunctionXT.replaceAll("(?<![a-zA-Z])t(?![a-zA-Z])", String.valueOf((double) i / n)));
        }

        boolean hasExactSolution = !exactSolutionInput.equalsIgnoreCase("no");
        String exactFunctionXT = "";
        if (hasExactSolution) {
            exactFunctionXT = exactSolutionInput.equalsIgnoreCase("test") ? "sin(2*" + Math.PI + "*x)*cos(2*" + Math.PI + "*t)" : exactSolutionInput;
        }

        List<String> omegas = new ArrayList<>();
        for (int i = 1; i <= m; i++) {
            omegas.add(omegaGenerator(i));
        }

        double tau = 1d / n;
        double[][] matrix = new double[(n + 1) * m][(n + 1) * m];
        UnivariateIntegrator integrator = new SimpsonIntegrator();

        System.out.println("Шаг 1: Формирование матрицы...");
        for (int i = 0; i < m; i++) {
            final int powI = i + 1;
            for (int j = 0; j < m; j++) {
                final int powJ = j + 1;
                UnivariateFunction productFunc = x -> (Math.pow(x, powI) * (x - 1)) * (Math.pow(x, powJ) * (x - 1));
                UnivariateFunction derivativeFunc = x -> ((powI + 1) * Math.pow(x, powI) - powI * Math.pow(x, powI - 1)) * ((powJ + 1) * Math.pow(x, powJ) - powJ * Math.pow(x, powJ - 1));

                double integral1 = integrator.integrate(1000000, productFunc, 0, 1);
                double integral2 = integrator.integrate(1000000, derivativeFunc, 0, 1);

                matrix[i][j] = -1.0 / tau * integral1 + 0.5 * integral2;
                matrix[i][j + m] = 1.0 / tau * integral1 + 0.5 * integral2;
            }
        }

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

        for (int i = 0; i < m; i++) {
            int row = n * m + i;
            matrix[row][i] = 1.0;
            matrix[row][n * m + i] = -1.0;
        }

        System.out.println("\nШаг 2: Формирование правого вектора...");
        double[] rightVector = new double[(n + 1) * m];
        for (int k = 1; k <= n; k++) {
            double t_k = (double) k / n;
            double t_k_minus_1 = (double) (k - 1) / n;
            String f_at_tk = valueInPoint.get(t_k);
            String f_at_tk_minus_1 = valueInPoint.get(t_k_minus_1);
            String f_averaged_str = "((" + f_at_tk + ") + (" + f_at_tk_minus_1 + "))/2.0";
            for (int i = 0; i < m; i++) {
                String currentOmega = omegas.get(i);
                Expression expression = new ExpressionBuilder("(" + f_averaged_str + ")*(" + currentOmega + ")").variable("x").build();
                UnivariateFunction function = x -> {
                    expression.setVariable("x", x);
                    return expression.evaluate();
                };
                rightVector[(k - 1) * m + i] = integrator.integrate(1000000, function, 0d, 1d);
            }
        }

        System.out.println("Шаг 3: Решение системы СЛАУ...");
        RealMatrix coefficients = MatrixUtils.createRealMatrix(matrix);
        RealVector constants = MatrixUtils.createRealVector(rightVector);
        DecompositionSolver solver = new LUDecomposition(coefficients).getSolver();
        RealVector solution = solver.solve(constants);

        Map<Double, String> solutionOfTask = new HashMap<>();
        for (int i = 0; i <= n; i++) {
            String expr = "";
            for (int k = 0; k < m; k++) {
                if (k > 0) expr += "+";
                expr += "(" + solution.getEntry(i * m + k) + ")*(" + omegas.get(k) + ")";
            }
            solutionOfTask.put((double) i / n, expr);
        }

        Map<Double, String> exactMap = new HashMap<>();
        if (hasExactSolution) {
            for (int i = 0; i <= n; i++) {
                exactMap.put((double) i / n, exactFunctionXT.replaceAll("(?<![a-zA-Z])t(?![a-zA-Z])", String.valueOf((double) i / n)));
            }
        }

        System.out.println("Шаг 4: Построение графиков...");
        generateCharts(solutionOfTask, exactMap, hasExactSolution);
        System.out.println("Вычисления завершены! Используйте ползунок справа для просмотра графиков.");
    }

    private void generateCharts(Map<Double, String> approxMap, Map<Double, String> exactMap, boolean hasExactSolution) {
        generatedCharts = new ArrayList<>();
        generatedTimes = new ArrayList<>();
        double globalMaxDiff = 0;

        List<Double> sortedKeys = new ArrayList<>(approxMap.keySet());
        Collections.sort(sortedKeys);

        for (Double t : sortedKeys) {
            generatedTimes.add(t);
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
                exactExpr = new ExpressionBuilder(exactMap.get(t)).variable("x").build();
            }

            int points = 100;
            double[] xData = new double[points];
            double[] yApprox = new double[points];
            double[] yExact = new double[points];
            double maxDiff = 0;

            for (int i = 0; i < points; i++) {
                double x = i / (double) (points - 1);
                xData[i] = x;
                double approxVal = approxExpr.setVariable("x", x).evaluate();
                yApprox[i] = approxVal;

                if (hasExactSolution) {
                    double exactVal = exactExpr.setVariable("x", x).evaluate();
                    yExact[i] = exactVal;
                    maxDiff = Math.max(maxDiff, Math.abs(approxVal - exactVal));
                }
            }

            XYSeries approxSeries = chart.addSeries("Приближенное решение", xData, yApprox);
            approxSeries.setMarker(SeriesMarkers.NONE);
            approxSeries.setLineColor(Color.RED); // Сделал красным, чтобы лучше выделялось
            approxSeries.setLineStyle(SeriesLines.SOLID);

            if (hasExactSolution) {
                globalMaxDiff = Math.max(globalMaxDiff, maxDiff);
                XYSeries exactSeries = chart.addSeries("Точное решение", xData, yExact);
                exactSeries.setMarker(SeriesMarkers.NONE);
                exactSeries.setLineColor(Color.BLACK);
                exactSeries.setLineStyle(SeriesLines.DASH_DASH);

                System.out.printf("Погрешность t=%.2f : %.6f\n", t, maxDiff);
            }

            generatedCharts.add(chart);
        }

        if (hasExactSolution) {
            System.out.printf("\nГЛОБАЛЬНАЯ ПОГРЕШНОСТЬ: %.6f\n", globalMaxDiff);
        }

        SwingUtilities.invokeLater(() -> {
            timeSlider.setMaximum(generatedCharts.size() - 1);
            timeSlider.setValue(0);
            timeSlider.setEnabled(true);
            displayChartAtIndex(0);
        });
    }

    private String omegaGenerator(int i) {
        return "x^%s*(x-1)".formatted(i);
    }

    private void redirectSystemOut() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) {
                updateTextArea(String.valueOf((char) b));
            }
            @Override
            public void write(byte[] b, int off, int len) {
                updateTextArea(new String(b, off, len));
            }
            private void updateTextArea(final String text) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append(text);
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                });
            }
        };
        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(out, true));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Diplom().setVisible(true));
    }
}