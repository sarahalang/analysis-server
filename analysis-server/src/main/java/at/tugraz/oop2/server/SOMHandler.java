package at.tugraz.oop2.server;

import java.awt.*;
import java.util.*;
import java.util.List;

import at.tugraz.oop2.data.DataSeries;
import at.tugraz.oop2.data.DataPoint;
import at.tugraz.oop2.data.ClusterDescriptor;
import at.tugraz.oop2.data.SOMQueryParameters;

public final class SOMHandler {
    private final int height;
    private final int width;
    private List<DataSeries> data;
    private List<ClusterDescriptor> clusterDescriptors;

    private final int maxIterationCount;
    private int iteration;
    private double learningRate;
    private final double learningRateDecrease;
    private double updateRadius;
    private final double getUpdateRadiusDecrease;

    private int intermediatePlots;
    private final SOMQueryParameters params;

    public SOMHandler(List<DataSeries> data, int height, int width,
                      double learningRate, double updateRadius, int maxIterationCount,
                      int intermediatePlots, SOMQueryParameters params) {
        this.data = new ArrayList<DataSeries>();
        for (DataSeries series: data) {
            if (series.getSensor().getType().contains("WARN"))
                returnWarning(series.getSensor().getType());
            else
                this.data.add(series);
        }
        this.height = height;
        this.width = width;
        this.clusterDescriptors = new ArrayList<ClusterDescriptor>(width * height);

        this.learningRate = learningRate;
        this.learningRateDecrease = learningRate / maxIterationCount;
        this.updateRadius = updateRadius;
        this.getUpdateRadiusDecrease = updateRadius / maxIterationCount;

        this.iteration = 0;
        this.maxIterationCount = maxIterationCount;
        this.intermediatePlots = intermediatePlots;
        this.params = params;
    }

    public List<ClusterDescriptor> clustering() {
        List<List<Double>> normalizedData = normalizeData(data);
        initializePrototypeVectors();
        for (; iteration <maxIterationCount; iteration++) {
            clearAllMemberLists();
            clusterIteration(normalizedData);

            if(iteration != 0 && (iteration - 1) % (maxIterationCount / intermediatePlots) == 0)
                returnIntermediateResult();
                //TODO: send intermediatePlot
        }
        return clusterDescriptors;
    }

    public void clusterIteration(List<List<Double>> normalizedData) {
        int positionInList = 0;
        for (List<Double> x : normalizedData) {
            // TODO: calc Euclidean Distance c: VO Seite 31: c = argmin(x - m_i)
            Point bestMatchingUnit = calculateBestMatchingUnit(x);
            // TODO: map normalizedData to best matching Prototype Vector
            int m_cIndex = (bestMatchingUnit.y * width) + bestMatchingUnit.x;
            clusterDescriptors.get(m_cIndex).addMember(data.get(positionInList));
//            System.out.println("Cluster desc has " + clusterDescriptors.get(m_cIndex).getMembers().size() + " members\nx has ");

            // TODO: adjust m_i to the data Vector during interation steps
            adjustM_i(x, m_cIndex);

            positionInList++;
        }
        updateLearningVaribles();
    }

    private void updateLearningVaribles() {
        learningRate -= learningRateDecrease;
        updateRadius -= getUpdateRadiusDecrease;
    }

    private void adjustM_i(List<Double> x, int m_cIndex) {
        ClusterDescriptor m_c = clusterDescriptors.get(m_cIndex);
        //System.out.println("Height: " + height + " width: " + width + " updateradius: " + updateRadius);
        //System.out.println("M_c y: " + m_c.getHeigthIndex() + " x: " + m_c.getWidthIndex());
        for(ClusterDescriptor m_i : clusterDescriptors) {
            if (calcEuclidianDistance(m_c.getWeights(), m_i.getWeights()) < updateRadius) {
//                System.out.println("Within the Radius");
                assert x.size() != m_i.getWeights().size() : "Dimension not correct!";

                List<Double> weights = m_i.getWeights();
                for (int i = 0; i < weights.size(); i++) {
                    weights.set(i, weights.get(i) + learningRate * (x.get(i) - weights.get(i)));
                }
                m_i.setWeights(weights);
            }
        }
    }

    private List<List<Double>> normalizeData(List<DataSeries> dataSeriesList) {
        List<DataPoint> temp = getMinMaxVectors(dataSeriesList);
        Double min = temp.get(0).getValue();
        Double max = temp.get(1).getValue();
        List<List<Double>> normalizedDataList = new ArrayList<List<Double>>();

        for (DataSeries dataSeries : dataSeriesList) {
            List<Double> normalizedData = new ArrayList<Double>();
            for (DataPoint dataPoint : dataSeries) {
                assert min > dataPoint.getValue() || max <dataPoint.getValue() : "MinMax not correct!";
                normalizedData.add((dataPoint.getValue() - min)/(max - min));
            }
            normalizedDataList.add(normalizedData);
        }
        return normalizedDataList;
    }

    private Point calculateBestMatchingUnit(List<Double> x) {
        Double smallestDistance = Double.MAX_VALUE;
        Point m_c = new Point();
        for (ClusterDescriptor cluster : clusterDescriptors) {
            double squaredSum = 0;
            for (int j = 0; j < x.size(); j++) {
                //System.out.println("Vector is " + vector.get(j));
                //System.out.println("Weights are " + cluster.getWeights().get(j));
                squaredSum += Math.pow(x.get(j) - cluster.getWeights().get(j), 2);
            }
            double distance = Math.sqrt(squaredSum);
            if (distance < smallestDistance) {
                smallestDistance = distance;
                m_c.y = cluster.getHeigthIndex();
                m_c.x = cluster.getWidthIndex();
            }
        }
//        System.out.println("For M_c we get (" + m_c.x + "|" + m_c.y + ")" +smallestDistance);
        return m_c;
    }

    private Double calcEuclidianDistance(List<Double> a, List<Double> b) {
        assert a.size() == b.size() : "Vectors have not the same size!";
        double squaredSum = 0;
        for (int j = 0; j < a.size(); j++) {
            squaredSum += Math.pow(a.get(j) - b.get(j), 2);
        }
        return Math.sqrt(squaredSum);
    }

    private List<DataPoint> getMinMaxVectors(List<DataSeries> dataSeriesList) {
        DataPoint minDatapoint = dataSeriesList.get(0).first();
        DataPoint maxDataPoint = dataSeriesList.get(0).first();

        for (DataSeries dataSeries : dataSeriesList) {
            for (DataPoint dataPoint : dataSeries) {
                if (dataPoint.getValue() < minDatapoint.getValue()) {
                    minDatapoint = dataPoint;
                }
                else if (dataPoint.getValue() > maxDataPoint.getValue()) {
                    maxDataPoint = dataPoint;
                }
            }
        }
        return Arrays.asList(minDatapoint, maxDataPoint);
    }

    private void initializePrototypeVectors() {
        Random random = new Random();
        int numOfDimensions = data.get(0).size();

        for (int i  = 0; i < height * width; i++) {
            List<Double> weights = new ArrayList<Double>();
            for (int dim = 0; dim < numOfDimensions; dim++) {
                weights.add(random.nextDouble());
            }
            clusterDescriptors.add(new ClusterDescriptor(i/height, i%width, weights));
//            System.out.println("initializePrototypeVectors [H:"+clusterDescriptors.get(i).getHeigthIndex() +
//                    " |W:" + clusterDescriptors.get(i).getWidthIndex() + "]: Weights: " + clusterDescriptors.get(i).getWeights());
        }
    }

    private void clearAllMemberLists() {
        for (ClusterDescriptor clusterDescriptor : clusterDescriptors) {
            clusterDescriptor.deleteMember();
        }
    }

    private void returnIntermediateResult() {
//        System.out.println("Send Intermediate Plot");
        ClientHandlerThread.sendIntermediateResult(this.clusterDescriptors, this.params, this.iteration);
    }

    private void returnWarning(String msg) {
//        System.out.println("Send Warning");
        ClientHandlerThread.warnReturn(msg);
    }
}
