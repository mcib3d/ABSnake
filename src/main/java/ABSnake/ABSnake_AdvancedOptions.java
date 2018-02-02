package ABSnake;


import ij.*;
import ij.plugin.*;
import ij.gui.*;

public class ABSnake_AdvancedOptions implements PlugIn {

    @Override
    public void run(String arg) {
        // dialog
        GenericDialog gd = new GenericDialog("Snake Advanced", IJ.getInstance());
        gd.addNumericField("Distance_Search", Prefs.get("ABSnake_DistSearch.int", 100), 0);
        gd.addNumericField("Displacement_min", Prefs.get("ABSnake_DisplMin.double", 0.1), 2);
        gd.addNumericField("Displacement_max", Prefs.get("ABSnake_DisplMax.double", 2.0), 2);
        gd.addNumericField("Threshold_dist_positive", Prefs.get("ABSnake_ThreshDistPos.double", 100), 0);
        gd.addNumericField("Threshold_dist_negative", Prefs.get("ABSnake_ThreshDistNeg.double", 100), 0);
        gd.addNumericField("Inv_alpha_min", Prefs.get("ABSnake_InvAlphaMin.double", 0.5), 2);
        gd.addNumericField("Inv_alpha_max", Prefs.get("ABSnake_InvAlphaMax.double", 2.0), 2);
        gd.addNumericField("Reg_min", Prefs.get("ABSnake_RegMin.double", 1), 2);
        gd.addNumericField("Reg_max", Prefs.get("ABSnake_RegMax.double", 2), 2);
        gd.addNumericField("Mul_factor", Prefs.get("ABSnake_MulFactor.double", 0.99), 4);
        // show dialog
        gd.showDialog();
        Prefs.set("ABSnake_DistSearch.int", (int) gd.getNextNumber());
        Prefs.set("ABSnake_DisplMin.double", gd.getNextNumber());
        Prefs.set("ABSnake_DisplMax.double", gd.getNextNumber());
        Prefs.set("ABSnake_ThreshDistPos.double", gd.getNextNumber());
        Prefs.set("ABSnake_ThreshDistNeg.double", gd.getNextNumber());
        Prefs.set("ABSnake_InvAlphaMin.double", gd.getNextNumber());
        Prefs.set("ABSnake_InvAlphaMax.double", gd.getNextNumber());
        Prefs.set("ABSnake_RegMin.double", gd.getNextNumber());
        Prefs.set("ABSnake_RegMax.double", gd.getNextNumber());
        Prefs.set("ABSnake_MulFactor.double", gd.getNextNumber());
    }
}
