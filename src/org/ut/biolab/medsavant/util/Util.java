/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ut.biolab.medsavant.util;

import com.jidesoft.swing.JideButton;
import fiume.vcf.VariantRecord;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import javax.swing.JButton;
import org.ut.biolab.medsavant.model.record.FileRecordModel;
import org.ut.biolab.medsavant.model.record.VariantRecordModel;

/**
 *
 * @author mfiume
 */
public class Util {

    public static Vector listToVector(List l) {
        Vector v = new Vector(l.size());
        v.addAll(l);
        return v;
    }

    public static Vector getVariantRecordsVector(List<VariantRecord> list) {
        Vector result = new Vector();
        for (VariantRecord r : list) {
            Vector v = VariantRecordModel.convertToVector(r);
            result.add(v);
        }
        return result;
    }

    public static Vector getFileRecordVector(List<FileRecordModel> list) {
        Vector result = new Vector();
        for (FileRecordModel r : list) {
            Vector v = FileRecordModel.convertToVector(r);
            result.add(v);
        }
        return result;
    }

   private static Random numGen = new Random();

   public static Color getRandomColor() {
      return new Color(numGen.nextInt(256), numGen.nextInt(256), numGen.nextInt(256));
   }

   public static boolean isQuantatitiveClass(Class c) {
        if (c == Integer.class || c == Long.class || c == Short.class || c == Double.class || c == Float.class) { return true; }
        return false;
    }


   public static Object parseStringValueAs(Class c, String value) {

        if (c == String.class) {
            return value;
        }
        if (c == Long.class) {
            try {
                return Long.parseLong(value);
            } catch (Exception e) {
                return null;
            }
        }

        if (c == Float.class) {
            try {
                return Float.parseFloat(value);
            } catch (Exception e) {
                return null;
            }
        }

        throw new UnsupportedOperationException("Parser doesn't deal with objects of type " + c);
    }
}