package ru.dbocharov;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
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

    private JTextField tfPoints, tfM, tfA, tfB, tfRightFunc, tfExactFunc;
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

        setTitle("Проекционно-разностный метод (SVD Псевдообратная матрица)");
        setSize(1150, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(420);

        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel inputPanel = new JPanel(new GridLayout(12, 1, 5, 2));

        inputPanel.add(new JLabel("Количество точек разбиения по времени (n):"));
        tfPoints = new JTextField("10");
        inputPanel.add(tfPoints);

        inputPanel.add(new JLabel("Количество базисных функций (m):"));
        tfM = new JTextField("5");
        inputPanel.add(tfM);

        inputPanel.add(new JLabel("Начало отрезка по x (a):"));
        tfA = new JTextField("0");
        inputPanel.add(tfA);

        inputPanel.add(new JLabel("Конец отрезка по x (b):"));
        tfB = new JTextField("1");
        inputPanel.add(tfB);

        inputPanel.add(new JLabel("Функция f(x,t) [или test]:"));
        tfRightFunc = new JTextField("test");
        inputPanel.add(tfRightFunc);

        inputPanel.add(new JLabel("Точное решение u(x,t) [test, no или формула]:"));
        tfExactFunc = new JTextField("test");
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
        double a, b;
        String rightFuncText = tfRightFunc.getText().trim();
        String exactFuncText = tfExactFunc.getText().trim();

        try {
            countPoint = Integer.parseInt(tfPoints.getText().trim());
            m = Integer.parseInt(tfM.getText().trim());
            a = Double.parseDouble(tfA.getText().trim().replace(",", "."));
            b = Double.parseDouble(tfB.getText().trim().replace(",", "."));

            if (a >= b) {
                System.out.println("Ошибка: Начало отрезка (a) должно быть меньше конца (b)!");
                resetButton();
                return;
            }
        } catch (NumberFormatException ex) {
            System.out.println("Ошибка: Поля n и m должны быть целыми, а a и b - числами!");
            resetButton();
            return;
        }

        new Thread(() -> {
            try {
                runMathLogic(countPoint, m, rightFuncText, exactFuncText, a, b);
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
            timeSlider.setEnabled(true);
        });
    }

    private double fastSimpsonIntegrate(Function f, double a, double b, int n) {
        if (n % 2 != 0) n++;
        double h = (b - a) / n;
        double sum = f.evaluate(a) + f.evaluate(b);

        for (int i = 1; i < n; i++) {
            double x = a + i * h;
            sum += f.evaluate(x) * (i % 2 == 0 ? 2 : 4);
        }
        return sum * h / 3.0;
    }

    private interface Function {
        double evaluate(double x);
    }

    private void runMathLogic(int countPoint, int m, String inputFunctionXT, String exactSolutionInput, double a, double b) {
        int n = countPoint + 1;
        double L = b - a;
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
            omegas.add(omegaGenerator(i, a, b, L));
        }

        double tau = 1d / n;
        double[][] matrix = new double[(n + 1) * m][(n + 1) * m];

        System.out.println("Шаг 1: Формирование матрицы Грама...");
        for (int i = 0; i < m; i++) {
            final int powI = i + 1;
            for (int j = 0; j < m; j++) {
                final int powJ = j + 1;

                Function productFunc = x -> {
                    double xi = (x - a) / L;
                    double xb = (x - b) / L;
                    return (Math.pow(xi, powI) * xb) * (Math.pow(xi, powJ) * xb);
                };

                Function derivativeFunc = x -> {
                    double xi = (x - a) / L;
                    double xb = (x - b) / L;
                    double d1 = (powI * Math.pow(xi, powI - 1) * xb + Math.pow(xi, powI)) / L;
                    double d2 = (powJ * Math.pow(xi, powJ - 1) * xb + Math.pow(xi, powJ)) / L;
                    return d1 * d2;
                };

                double integral1 = fastSimpsonIntegrate(productFunc, a, b, 1000);
                double integral2 = fastSimpsonIntegrate(derivativeFunc, a, b, 1000);

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

        System.out.println("Шаг 2: Формирование правого вектора...");
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

                Function function = x -> {
                    return expression.setVariable("x", x).evaluate();
                };

                rightVector[(k - 1) * m + i] = fastSimpsonIntegrate(function, a, b, 500);
            }
        }

        System.out.println("Шаг 3: Решение СЛАУ через SVD (Псевдообратная матрица)...");
        RealMatrix coefficients = MatrixUtils.createRealMatrix(matrix);
        RealVector constants = MatrixUtils.createRealVector(rightVector);

        // ВОТ ЗДЕСЬ ИСПОЛЬЗУЕТСЯ SVD ДЛЯ НАХОЖДЕНИЯ ПСЕВДООБРАТНОЙ МАТРИЦЫ!
        SingularValueDecomposition svd = new SingularValueDecomposition(coefficients);
        DecompositionSolver solver = svd.getSolver();

        System.out.println("Обусловленность матрицы: " + svd.getConditionNumber());
        if (!solver.isNonSingular()) {
            System.out.println("ВНИМАНИЕ: Матрица вырождена! Применяется псевдообратная матрица Мура-Пенроуза.");
        }

        RealVector solution = solver.solve(constants);

        Map<Double, double[]> solutionOfTask = new HashMap<>();
        for (int i = 0; i <= n; i++) {
            double[] coeffs = new double[m];
            for (int k = 0; k < m; k++) {
                coeffs[k] = solution.getEntry(i * m + k);
            }
            solutionOfTask.put((double) i / n, coeffs);
        }

        Map<Double, String> exactMap = new HashMap<>();
        if (hasExactSolution) {
            for (int i = 0; i <= n; i++) {
                exactMap.put((double) i / n, exactFunctionXT.replaceAll("(?<![a-zA-Z])t(?![a-zA-Z])", String.valueOf((double) i / n)));
            }
        }

        System.out.println("Шаг 4: Построение графиков...");
        generateCharts(solutionOfTask, exactMap, hasExactSolution, a, b, L);
        System.out.println("Вычисления завершены!");
    }

    private void generateCharts(Map<Double, double[]> approxMap, Map<Double, String> exactMap, boolean hasExactSolution, double a, double b, double L) {
        generatedCharts = new ArrayList<>();
        generatedTimes = new ArrayList<>();
        double globalMaxDiff = 0;

        List<Double> sortedKeys = new ArrayList<>(approxMap.keySet());
        Collections.sort(sortedKeys);

        for (Double t : sortedKeys) {
            generatedTimes.add(t);
            double[] coeffs = approxMap.get(t);

            XYChart chart = new XYChartBuilder()
                    .width(800).height(600)
                    .title("Решение при t = " + String.format("%.2f", t))
                    .xAxisTitle("x").yAxisTitle("U(x)")
                    .build();

            chart.getStyler().setLegendVisible(true);
            chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);

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
                double x = a + i * (b - a) / (points - 1);
                xData[i] = x;

                double approxVal = 0;
                for (int k = 0; k < coeffs.length; k++) {
                    double xi = (x - a) / L;
                    double xb = (x - b) / L;
                    approxVal += coeffs[k] * Math.pow(xi, k + 1) * xb;
                }
                yApprox[i] = approxVal;

                if (hasExactSolution) {
                    double exactVal = exactExpr.setVariable("x", x).evaluate();
                    yExact[i] = exactVal;
                    maxDiff = Math.max(maxDiff, Math.abs(approxVal - exactVal));
                }
            }

            XYSeries approxSeries = chart.addSeries("Приближенное решение", xData, yApprox);
            approxSeries.setMarker(SeriesMarkers.NONE);
            approxSeries.setLineColor(Color.RED);
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
            displayChartAtIndex(0);
        });
    }

    private String omegaGenerator(int i, double a, double b, double L) {
        return "(((x-(" + a + "))/" + L + ")^" + i + "*((x-(" + b + "))/" + L + "))";
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