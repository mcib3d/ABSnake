package ABSnake;


/*
 *  Active Contours ( "Snakes")
 *  Initialization by a rectangle, oval or closed area
 *  Deformation of the snake towards the nearest edges along the
 *  normals, edges computed by a deriche filter and above the threshold
 *  value.
 *  Regularization of the shape by constraing the points to form a
 *  smooth curve (depending on the value of regularization)
 *  Works in 3D by propagating the snake found in slice i
 *  to slice i+1
 */
import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.*;
import ij.process.*;

import java.awt.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ABSnake_ plugin interface
 *
 * @author thomas.boudier@snv.jussieu.fr and Philippe Andrey
 * @created 26 aout 2003
 */
public class ABSnake_ implements PlugInFilter {
    // Sauvegarde de la fenetre d'image :

    ImagePlus imp;
    // Sauvegarde de la pile de debut et de resultat :
    ImageStack pile = null;
    ImageStack pile_resultat = null;
    ImageStack pile_seg = null;
    int currentSlice = -1;
    // Sauvegarde des dimensions de la pile :
    int profondeur = 0;
    int largeur = 0;
    int hauteur = 0;
    // ROI originale et courante
    int nbRois;
    Roi rorig = null;
    Roi processRoi = null;
    Color colorDraw = null;
    /**
     * Parametres du Snake :
     */
    SnakeConfigDriver configDriver;
    // number of iterations
    int ite = 50;
    // step to display snake
    int step = 1;
    // threshold of edges
    int seuil = 5;
    // how far to look for edges
    int DistMAX = Prefs.getInt("ABSnake_DistSearch.int", 100);
    // maximum displacement
    double force = 5.0;
    // regularization factors, min and max
    double reg = 5.0;
    double regmin, regmax;
    // first and last slice to process
    int slice1, slice2;
    // misc options
    boolean showgrad = false;
    boolean savecoords = false;
    boolean createsegimage = false;
    boolean advanced = false;
    boolean propagate = true;
    boolean movie = false;
    boolean saveiterrois = false;
    boolean useroinames = false;
    boolean nosizelessrois = false;
    //boolean differentfolder=false;
    String usefolder = IJ.getDirectory("imagej");
    String addToName = "";
    // String[] RoisNames;

    /**
     * Main processing method for the Snake_deriche_ object
     *
     * @param ip image
     */
    public void run(ImageProcessor ip) {
        // original stack
        pile = imp.getStack();
        // sizes of the stack
        profondeur = pile.getSize();
        largeur = pile.getWidth();
        hauteur = pile.getHeight();
        slice1 = 1;
        slice2 = profondeur;
        Calibration cal = imp.getCalibration();
        double resXY = cal.pixelWidth;

        boolean dialog = Dialogue();

        // many rois
        RoiManager roimanager = RoiManager.getInstance();
        if (roimanager == null) {
            roimanager = new RoiManager();
            roimanager.setVisible(true);
            rorig = imp.getRoi();
            if (rorig == null) {
                IJ.showMessage("Roi required");
            } else {
                roimanager.add(imp, rorig, 0);
            }
        }
        //Hashtable tableroi = roimanager.getROIs();
        nbRois = roimanager.getCount();
        IJ.log("processing " + nbRois + "rois");
        Roi[] RoisOrig = roimanager.getRoisAsArray();
        Roi[] RoisCurrent = new Roi[nbRois];
        Roi[] RoisResult = new Roi[nbRois];
        System.arraycopy(RoisOrig, 0, RoisCurrent, 0, nbRois);

        //RoisNames=new String[nbRois];
        //for (int i = 0; i < nbRois; i++) {
        //RoisNames[i]=roimanager.getName(i);
        //}
        if (dialog) {
            configDriver = new SnakeConfigDriver();
            AdvancedParameters();
            // ?
            regmin = reg / 2.0;
            regmax = reg;
            // ?
            // init result
            pile_resultat = new ImageStack(largeur, hauteur, java.awt.image.ColorModel.getRGBdefault());
            if (createsegimage) {
                pile_seg = new ImageStack(largeur, hauteur);
            }
            // update of the display
            String label = "" + imp.getTitle();
            for (int z = 0; z < profondeur; z++) {
                pile_resultat.addSlice(label, pile.getProcessor(z + 1).duplicate().convertToRGB());
            }
            //final ImagePlus imp_resultat = new ImagePlus(imp.getTitle() + "_ABsnake_", pile_resultat);
            //imp_resultat.show();
            int nbcpu = 1;
            Thread[] threads = new Thread[nbcpu];
            AtomicInteger k = new AtomicInteger(0);
            ABSnake[] snakes = new ABSnake[RoisOrig.length];

            // for all slices
            // display in RGB color
            ColorProcessor image;
            ImagePlus plus;

            // NEW LOOP 15/12/2015
            Roi roi;
            ABSnake snake;
            RoiEncoder saveRoi;
            ByteProcessor seg = null;
            int sens = slice1 < slice2 ? 1 : -1;
            for (int z = slice1; z != (slice2 + sens); z += sens) {
                ColorProcessor imageDraw = (ColorProcessor) (pile_resultat.getProcessor(z).duplicate());
                image = (ColorProcessor) (pile_resultat.getProcessor(z).duplicate());
                plus = new ImagePlus("Roi " + z, image);
                for (int i = 0; i < RoisOrig.length; i++) {
                    if (createsegimage) {
                        seg = new ByteProcessor(pile_seg.getWidth(), pile_seg.getHeight());
                    }
                    if (propagate) {
                        // imp.setRoi(RoisCurrent[i]);
                        roi = RoisCurrent[i];
                    } else {
                        // imp.setRoi(RoisOrig[i]);
                        roi = RoisOrig[i];
                    }
                    IJ.log("processing slice " + z + " with roi " + i);
                    snake = processSnake(plus, roi, z, i + 1);
                    snake.killImages();

                    snake.DrawSnake(imageDraw, colorDraw, 1);
                    //pluses[i].hide();
                    RoisResult[i] = snake.createRoi();
                    RoisResult[i].setName("res-" + i);
                    RoisCurrent[i] = snake.createRoi();

                    pile_resultat.setPixels(imageDraw.getPixels(), z);

                    if (createsegimage) {
                        seg.copyBits(snake.segmentation(seg.getWidth(), seg.getHeight(), i + 1), 0, 0, Blitter.ADD);
                        seg.resetMinAndMax();
                        pile_seg.addSlice("Seg " + z, seg);
                    } // segmentation

                    if (savecoords) {
                        snake.writeCoordinates(usefolder + "//" + "ABSnake-r" + (i + 1) + "-z", z, resXY);
                        if (nosizelessrois == false || (nosizelessrois == true && RoisResult[i].getFloatWidth() > 2 && RoisResult[i].getFloatHeight() > 2)) {
                            try {
                                saveRoi = new RoiEncoder(usefolder + "//" + "ABSnake-r" + (i + 1) + "-z" + z + ".roi");
                                saveRoi.write(RoisResult[i]);
                            } catch (IOException ex) {
                                Logger.getLogger(ABSnake_.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    } // save coord
                }
            }

//            for (int z = slice1; z != (slice2 + sens); z += sens) {
//                final int zz = z;
//                k.set(0);
//
//                for (int i = 0; i < RoisOrig.length; i++) {
//                    image[i] = (ColorProcessor) (pile_resultat.getProcessor(zz).duplicate());
//                    pluses[i] = new ImagePlus("Roi " + i, image[i]);
//                    //pluses[i].show();
//                }
//
//                // for all rois
//                for (int t = 0; t < threads.length; t++) {
//                    threads[t] = new Thread() {
//
//                        @Override
//                        public void run() {
//                            IJ.wait(1000);
//                            Roi roi = null;
//                            //for (int i = 0; i < RoisOrig.length; i++) {
//
//                            for (int i = k.getAndIncrement(); i < RoisOrig.length; i = k.getAndIncrement()) {
//
//                                if (propagate) {
//                                    // imp.setRoi(RoisCurrent[i]);
//                                    roi = RoisCurrent[i];
//                                } else {
//                                    // imp.setRoi(RoisOrig[i]);
//                                    roi = RoisOrig[i];
//                                }
//                                IJ.log("processing slice " + zz + " with roi " + i);
//
//                                snakes[i] = processSnake(pluses[i], roi, zz, i + 1);
//                                snakes[i].killImages();
//                                // RoisCurrent[i] = imp.getRoi();
//                                // imp_resultat.updateAndRepaintWindow();
//                            } // for roi
//                        }
//                    };
//                }// for threads
//
//                // launch threads
//                for (int ithread = 0; ithread < threads.length; ++ithread) {
//                    threads[ithread].setPriority(Thread.NORM_PRIORITY);
//                    threads[ithread].start();
//                }
//
//                try {
//                    for (int ithread = 0; ithread < threads.length; ++ithread) {
//                        threads[ithread].join();
//                    }
//                } catch (InterruptedException ie) {
//                    throw new RuntimeException(ie);
//                }
//                // threads finished
//
//                // display + rois
//                //RoiEncoder saveRoi;
//                ColorProcessor imageDraw = (ColorProcessor) (pile_resultat.getProcessor(zz).duplicate());
//                for (int i = 0; i < RoisOrig.length; i++) {
//                    snakes[i].DrawSnake(imageDraw, colorDraw, 1);
//                    pluses[i].hide();
//                    RoisResult[i] = snakes[i].createRoi();
//                    RoisResult[i].setName("res-" + i);
//                    RoisCurrent[i] = snakes[i].createRoi();
//                    // add results roi to manager
//                    //roimanager.addRoi(RoisResult[i]);
//
//                }
//                pile_resultat.setPixels(imageDraw.getPixels(), z);
//
//                if (createsegimage) {
//                    ByteProcessor seg = new ByteProcessor(pile_seg.getWidth(), pile_seg.getHeight());
//                    ByteProcessor tmp;
//                    for (int i = 0; i < RoisOrig.length; i++) {
//                        tmp = snakes[i].segmentation(seg.getWidth(), seg.getHeight(), i + 1);
//                        seg.copyBits(tmp, 0, 0, Blitter.ADD);
//                    }
//                    seg.resetMinAndMax();
//                    pile_seg.addSlice("Seg " + z, seg);
//                    //new ImagePlus("seg " + zz, seg).show();
//                    //IJ.run("3-3-2 RGB");
//                    // IJ.run("Enhance Contrast", "saturated=0.0");
//                } // segmentation
//
//                if (savecoords) {
//                    for (int i = 0; i < RoisOrig.length; i++) {
//                        try {
//                            snakes[i].writeCoordinates(usefolder + "//" + "ABSnake-r" + (i + 1) + "-z", zz, resXY);
//                            if (nosizelessrois == false || (nosizelessrois == true && RoisResult[i].getFloatWidth() > 2 && RoisResult[i].getFloatHeight() > 2)) {
//                                saveRoi = new RoiEncoder(usefolder + "//" + "ABSnake-r" + (i + 1) + "-z" + zz + ".roi");
//                                saveRoi.write(RoisResult[i]);
//                            }
//                        } catch (IOException ex) {
//                            Logger.getLogger(ABSnake_.class.getName()).log(Level.SEVERE, null, ex);
//                        }
//                    }
//                } // save coord
//
//            } // for z
            new ImagePlus("Draw", pile_resultat).show();
            if (createsegimage) {
                new ImagePlus("Seg", pile_seg).show();
            }
        }// dialog
        System.gc();
    }

    /**
     * Dialog
     *
     * @return dialog ok ?
     */
    private boolean Dialogue() {
        // array of colors
        String[] colors = {"Red", "Green", "Blue", "Cyan", "Magenta", "Yellow", "Black", "White"};
        int indexcol = 0;
        // create dialog
        GenericDialog gd = new GenericDialog("Snake");
        gd.addNumericField("Gradient_threshold:", seuil, 0);
        gd.addNumericField("Number_of_iterations:", ite, 0);
        gd.addNumericField("Step_result_show:", step, 0);
        //if (profondeur == 1) {
        gd.addCheckbox("Save intermediate images", movie);
        //}
        if (profondeur > 1) {
            gd.addNumericField("First_slice:", slice1, 0);
            gd.addNumericField("Last_slice:", slice2, 0);
            gd.addCheckbox("Propagate roi", propagate);
        }
        gd.addChoice("Draw_color:", colors, colors[indexcol]);
        gd.addCheckbox("Save_coords:", savecoords);
        gd.addCheckbox("Create_seg_image:", createsegimage);
        gd.addCheckbox("Save_iteration_rois:", saveiterrois);
        //gd.addCheckbox("Use_roi_names:", useroinames);
        gd.addCheckbox("No_sizeless_rois:", nosizelessrois);
        //gd.addCheckbox("Use_different_folder", differentfolder);
        gd.addStringField("Use_folder:", usefolder);
        //gd.addCheckbox("Advanced_options", advanced);
        // show dialog
        gd.showDialog();

        // threshold of edge
        seuil = (int) gd.getNextNumber();

        // number of iterations
        ite = (int) gd.getNextNumber();
        // step of display
        step = (int) gd.getNextNumber();
        //if (profondeur == 1) {
        movie = gd.getNextBoolean();
        //}
        if (step > ite - 1) {
            IJ.showStatus("Warning : show step too big\n\t step assignation 1");
            step = 1;
        }
        if (profondeur > 1) {
            slice1 = (int) gd.getNextNumber();
            slice2 = (int) gd.getNextNumber();
            propagate = gd.getNextBoolean();
        }
        // color choice of display
        indexcol = gd.getNextChoiceIndex();
        switch (indexcol) {
            case 0:
                colorDraw = Color.red;
                break;
            case 1:
                colorDraw = Color.green;
                break;
            case 2:
                colorDraw = Color.blue;
                break;
            case 3:
                colorDraw = Color.cyan;
                break;
            case 4:
                colorDraw = Color.magenta;
                break;
            case 5:
                colorDraw = Color.yellow;
                break;
            case 6:
                colorDraw = Color.black;
                break;
            case 7:
                colorDraw = Color.white;
                break;
            default:
                colorDraw = Color.yellow;
        }
        savecoords = gd.getNextBoolean();
        createsegimage = gd.getNextBoolean();
        saveiterrois = gd.getNextBoolean();
        //useroinames=gd.getNextBoolean();
        nosizelessrois = gd.getNextBoolean();
        //differentfolder=gd.getNextBoolean();
        //Vector<?> stringFields=gd.getStringFields();
        //usefolder=((TextField) stringFields.get(0)).getText();
        usefolder = gd.getNextString();
        //advanced = gd.getNextBoolean();

        return !gd.wasCanceled();
    }

    /**
     * Dialog advanced
     *
     * @return dialog ok ?
     */
    private void AdvancedParameters() {
        // see advanced dialog class
        configDriver.setMaxDisplacement(Prefs.get("ABSnake_DisplMin.double", 0.1), Prefs.get("ABSnake_DisplMax.double", 2.0));
        configDriver.setInvAlphaD(Prefs.get("ABSnake_InvAlphaMin.double", 0.5), Prefs.get("ABSnake_InvAlphaMax.double", 2.0));
        configDriver.setReg(Prefs.get("ABSnake_RegMin.double", 0.1), Prefs.get("ABSnake_RegMax.double", 2.0));
        configDriver.setStep(Prefs.get("ABSnake_MulFactor.double", 0.99));
    }

    /**
     * do the snake algorithm on all images
     *
     * @param image RGB image to display the snake
     * @param numSlice which image of the stack
     */
    public ABSnake processSnake(ImagePlus plus, Roi roi, int numSlice, int numRoi) {
        //int x;
        //int y;
        //int max;
        //int min;
        //int p;
        int i;
        //int NbPoints;
        //double cou;
        //double cour;
        //double maxC;
        //double scale;
        SnakeConfig config;

        //IJ.log("process " + numRoi + " " + numSlice + " " + plus + " " + roi);
        // processRoi = imp.getRoi();
        processRoi = roi;

        // initialisation of the snake
        ABSnake snake = new ABSnake();
        snake.Init(processRoi);
        snake.setOriginalImage(pile.getProcessor(numSlice));

        // start of computation
        IJ.showStatus("Calculating snake...");
        //ColorProcessor image2;
        //image2 = (ColorProcessor) image.duplicate();
        // image2 = (ColorProcessor) (pile_resultat.getProcessor(numSlice).duplicate());

        //ImagePlus windowsTemp = new ImagePlus("Iteration", image2);
        if (step > 0) {
            plus.show();
        }

        double InvAlphaD = configDriver.getInvAlphaD(false);
        //double InvAlphaDMin = configDriver.getInvAlphaD(true);
        double regMax = configDriver.getReg(false);
        double regMin = configDriver.getReg(true);
        double DisplMax = configDriver.getMaxDisplacement(false);
        //double DisplMin = configDriver.getMaxDisplacement(true);
        double mul = configDriver.getStep();

        config = new SnakeConfig(seuil, DisplMax, DistMAX, regMin, regMax, 1.0 / InvAlphaD);
        snake.setConfig(config);
        // compute image gradient
        snake.computeGrad(pile.getProcessor(numSlice));

        IJ.resetEscape();
        FileSaver fs = new FileSaver(plus);

        double dist0 = 0.0;
        double dist;
        //double InvAlphaD0 = InvAlphaD;

        //if(useroinames==true){
        //addToName=RoisNames[numRoi];    
        //IJ.log("Selected to use names, has name: "+addToName);
        //}else{
        //  IJ.log("Not selected to use names");
        //}
        for (i = 0; i < ite; i++) {
            if (IJ.escapePressed()) {
                break;
            }
            // each iteration
            dist = snake.process();
            if ((dist >= dist0) && (dist < force)) {
                //System.out.println("update " + config.getAlpha());
                snake.computeGrad(pile.getProcessor(numSlice));
                config.update(mul);
            }
            dist0 = dist;

            // display of the snake
            if ((step > 0) && ((i % step) == 0)) {
                IJ.showStatus("Show intermediate result (iteration n" + (i + 1) + ")");
                ColorProcessor image2 = (ColorProcessor) (pile_resultat.getProcessor(numSlice).duplicate());

                snake.DrawSnake(image2, colorDraw, 1);
                plus.setProcessor("", image2);
                plus.setTitle(imp.getTitle() + " roi " + numRoi + " (iteration n" + (i + 1) + ")");
                plus.updateAndRepaintWindow();
                if (movie) {
                    fs = new FileSaver(plus);
                    fs.saveAsTiff(usefolder + "//" + addToName + "ABsnake-r" + numRoi + "-t" + i + "-z" + numSlice + ".tif");
                }
                RoiEncoder saveRoi;
                if (saveiterrois) {
                    try {
                        Roi roiToSave = snake.createRoi();
                        if (nosizelessrois == false || (nosizelessrois == true && roiToSave.getFloatWidth() > 2 && roiToSave.getFloatHeight() > 2)) {
                            saveRoi = new RoiEncoder(usefolder + "//" + addToName + "ABsnake-r" + numRoi + "-t" + i + "-z" + numSlice + ".roi");
                            saveRoi.write(roiToSave);
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(ABSnake_.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
            }
        }// end iteration

        // close temp window    
        //plus.hide();

        /*
         // draw
         snake.DrawSnake(image, colorDraw, 1);
         ByteProcessor segImage;
         if (createsegimage) {
         segImage = snake.segmentation(image.getWidth(), image.getHeight(), numRoi);
         segImage.setMinAndMax(0, nbRois);
         if (currentSlice != numSlice) {
         pile_seg.addSlice("" + numSlice, segImage);
         currentSlice = numSlice;
         } else {
         (pile_seg.getProcessor(currentSlice)).copyBits(segImage, 0, 0, Blitter.ADD);
         }
         }
         if (savecoords) {
         snake.writeCoordinates("ABsnake-" + numRoi + "-", numSlice);
         }
         * 
         */
        //processRoi = new PolygonRoi(0, 0, imp);
        //processRoi = snake.createRoi(imp);
        // snake.kill();
        //imp.killRoi();
        //imp.setRoi(processRoi);
        snake.setOriginalImage(null);

        return snake;
    }

    /**
     * setup
     *
     * @param arg arguments
     * @param imp image plus
     * @return setup
     */
    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        return DOES_8G + DOES_16 + DOES_32 + NO_CHANGES;
    }
}
