package at.tugraz.oop2.server;

import at.tugraz.oop2.Util;
import at.tugraz.oop2.data.DataSeries;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static at.tugraz.oop2.data.DataSeries.Operation;

public final class ClientCommand {

    enum TYPE {ERROR, LS, DATA, CLUSTER};
    private final TYPE cmd_;
    private int id_ = 0;
    private String metric_ = null;
    private LocalDateTime from_ = null; //inclusive
    private LocalDateTime to_ = null;   //exclusive
    private Operation op_ = null;
    private int interval_ = 0;
    private List<Integer> ids_ = null;
    private int length_ = 0;
    private int gridHeight_ = 0;
    private int gridWidth_ = 0;
    private double updateRadius_ = 0.0f;
    private double learningRate_ = 0.0f;
    private int itsPerCurve_ = 0;
    private int res_id_ = 0;
    private int int_res_ = 0;

    //Constructor
    public ClientCommand(ClientCommand.TYPE cmd, String metric, int id, LocalDateTime from, LocalDateTime to,
                         Operation op, int interval) {
        this.cmd_ = cmd;
        this.metric_ = metric;
        this.id_ = id;
        this.from_ = from;
        this.to_ = to;
        this.op_ = op;
        this.interval_ = interval;
    }
    //Constructor
    public ClientCommand(String input) {
        if(input.contains("=[")) {
            String sub = input.substring(input.indexOf("=["), input.indexOf("],") + 1);
            String sub1 = sub.replace(",", ";");
            sub1 = sub1.replace("[", "");
            sub1 = sub1.replace("]", "");
            input = input.replace(sub, sub1);
        }
       String[] tokens =  input.split("(, )");
       if(tokens.length == 0) {
           cmd_ = TYPE.ERROR;
           return;
       }

       boolean fillIdList = false;
       if(tokens[0].toLowerCase().equals("ls")) {
           cmd_ = TYPE.LS;
           return;
       } else if(tokens[0].toLowerCase().equals("data")) {
            cmd_ = TYPE.DATA;
            id_ = Integer.parseInt(tokens[1].substring(tokens[1].lastIndexOf('=') + 1));
            tokens[6] = StringUtils.removeEnd(tokens[6], ")");
        } else if(tokens[0].toLowerCase().equals("cluster")) {
           cmd_ = TYPE.CLUSTER;
           tokens[14] = StringUtils.removeEnd(tokens[14], ")");
           String[] ids = tokens[1].substring(tokens[1].lastIndexOf('=') + 1).split("; ");
           ids_ = new ArrayList<Integer>();
           if(ids.length > 1) {
               id_ = ids.length; // store count here
               for(int i = 0; i < id_; i++) {
                    ids_.add(Integer.parseInt(ids[i]));
               }
           } else { // if one id or cmd "all" store id as usual
               int id = Integer.parseInt(tokens[1].substring(tokens[1].lastIndexOf('=') + 1));
               if(id == -1) {
                  fillIdList = true;
               } else {
                   id_ = id;
                   ids_.add(id_);
               }
           }
           length_ = Integer.parseInt(tokens[7].substring(tokens[7].lastIndexOf('=') + 1));
           gridHeight_ = Integer.parseInt(tokens[8].substring(tokens[8].lastIndexOf('=') + 1));
           gridWidth_ = Integer.parseInt(tokens[9].substring(tokens[9].lastIndexOf('=') + 1));
           updateRadius_ = Double.parseDouble(tokens[10].substring(tokens[10].lastIndexOf('=') + 1));
           learningRate_ = Double.parseDouble(tokens[11].substring(tokens[11].lastIndexOf('=') + 1));
           itsPerCurve_ = Integer.parseInt(tokens[12].substring(tokens[12].lastIndexOf('=') + 1));
           res_id_ = Integer.parseInt(tokens[13].substring(tokens[13].lastIndexOf('=') + 1));
           int_res_ = Integer.parseInt(tokens[14].substring(tokens[14].lastIndexOf('=') + 1));
       } else {
           cmd_ = TYPE.ERROR;
           return;
       }
        metric_ = tokens[2].substring(tokens[2].lastIndexOf('=') + 1);
        from_ = Util.stringToLocalDateTime(tokens[3].substring(tokens[3].lastIndexOf('=') + 1));
        to_ = Util.stringToLocalDateTime(tokens[4].substring(tokens[4].lastIndexOf('=') + 1));
        op_ = StringToOP(tokens[5].substring(tokens[5].lastIndexOf('=') + 1));
        interval_ = Integer.parseInt(tokens[6].substring(tokens[6].lastIndexOf('=') + 1));

        if(fillIdList) {
            ids_ = SensorManager.getInstance().getAllSensorIdsByMetric(metric_);
            id_ = ids_.size();
        }
    }

    //Getter
    public TYPE getCmd() { return cmd_; }
    public int getID() { return id_; }
    public String getMetric() { return metric_; }
    public LocalDateTime getFrom() { return  from_; }
    public LocalDateTime getTo() { return  to_; }
    public DataSeries.Operation getOperation() { return op_; }
    public int getInterval() { return interval_; }
    public List<Integer> getIds() { return ids_; }
    public int getLength() { return length_; }
    public int getGridHeight_() { return gridHeight_; }
    public int getGridWidth_() { return gridWidth_; }
    public double getUpdateRadius_() { return updateRadius_; }
    public double getLearningRate_() { return learningRate_; }
    public int getItsPerCurve_() { return itsPerCurve_; }
    public int getRes_id_() { return res_id_; }
    public int getInt_res_() { return int_res_; }

    //Methods
    private DataSeries.Operation StringToOP(String cmd) {
        switch(cmd.toUpperCase()) {
            case "MIN":
                return DataSeries.Operation.MIN;
            case "MAX":
                return DataSeries.Operation.MAX;
            case "MEAN":
                return DataSeries.Operation.MEAN;
            case "MEDIAN":
                return DataSeries.Operation.MEDIAN;
            default:
                return DataSeries.Operation.NONE;
        }
    }

    public String ToString() {
        switch(cmd_) {
            case LS:
                return "Command: LS";
            case ERROR:
                return "Command: ERROR";
            case DATA:
                return "Command: Data\nID: " + id_ + " ;--; Metric: " + metric_ + "\nFrom: " + from_.toString() +
                        "  To: " + to_.toString() + "\nOP: " + op_.toString() + "  ;--; Int: " + interval_;
            case CLUSTER:
                StringBuilder ids = new StringBuilder("[");
                if(ids_ != null) {
                    for(int i : ids_) {
                        ids.append(i).append(";");
                    }
                } else {
                    ids.append(id_).append(";");
                }
                ids.replace(ids.length() - 1, ids.length(), "]");
                return "Command: Cluster\nIDs: " + ids.toString() + " ;--; Metric: " + metric_ + "\nFrom: " + from_.toString() +
                        "  To: " + to_.toString() + "\nOP: " + op_.toString() + "  ;--; Int: " + interval_ +
                        "\nLength: " + length_ + " ;--; Grid(H|W): (" + gridHeight_ + "|" + gridWidth_ + ")" +
                        "\nuRad: " + updateRadius_ + " ;--; lRa: " + learningRate_ + " ;--; it/C: " + itsPerCurve_ +
                        "\nResID: 0x" + String.format("%x", res_id_).toUpperCase() + " ;--; intermediate Results: " + int_res_;
            default:
                return "Default Case should not happen";
        }
    }
}
