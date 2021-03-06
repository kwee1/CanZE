/*
    CanZE
    Take a closer look at your ZE car

    Copyright (C) 2015 - The CanZE Team
    http://canze.fisch.lu

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or any
    later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/


/*
 * This class represents a field.
 * Other objects can register to be notified.
 */
package lu.fisch.canze.actors;

import lu.fisch.canze.activities.MainActivity;
import lu.fisch.canze.interfaces.FieldListener;

import java.util.ArrayList;
import java.util.Calendar;

/**
 *
 * @author robertfisch
 */
public class Field {

    protected final ArrayList<FieldListener> fieldListeners = new ArrayList<>();

    protected Frame frame;
    protected short from;
    protected short to;
    protected double offset;
    //private int divider;
    //private int multiplier;
    protected int decimals;
    protected double resolution;
    protected String unit;
    protected String requestId;
    protected String responseId;
    protected short options;           // bitwise options 0xf = car (0-15), 0x70 = type (0=unsigned, 1=signed, 2=reserved for string, 3-7 reserved)
    //private int skips;

    protected double value = Double.NaN;
    //private int skipsCount = 0;

    protected long lastRequest = 0;
    protected int interval = Integer.MAX_VALUE;

    protected boolean virtual = false;
    
    public Field(Frame frame, short from, short to, double resolution, int decimals, double offset, String unit, String requestId, String responseId, short options) {
        this.frame=frame;
        this.from=from;
        this.to=to;
        this.offset=offset;
        this.resolution = resolution;
        this.decimals = decimals;
        this.unit = unit;
        this.requestId=requestId;
        this.responseId=responseId;
        this.options=options;

        this.lastRequest=Calendar.getInstance().getTimeInMillis();
    }
    
    @Override
    public Field clone()
    {
        Field field = new Field(frame, from, to, resolution, decimals, offset, unit, requestId, responseId, options);
        field.value = value;
        field.lastRequest=lastRequest;
        field.interval=interval;
        return field;
    }
    
    @Override
    public String toString()
    {
        return getSID()+" : "+getPrintValue();
    }

    public boolean isIsoTp()
    {
        return !responseId.trim().isEmpty();
    }

    public String getSID()
    {
        if(responseId!=null && !responseId.trim().isEmpty())
            return (Integer.toHexString(frame.getId())+"."+responseId.trim()+"."+from).toLowerCase();
        else
            return (Integer.toHexString(frame.getId())+"."+from).toLowerCase();
    }

    public String getUniqueID()
    {
        return getCar()+"."+getSID();
    }
    
    public String getPrintValue()
    {
        return getValue()+" "+getUnit();
    }

    public String getStringValue()
    {
        // truncate to a long
        long longValue = (long) value;
        // prepare to cut into 8 bit pieces
        int[] intArray = new int[8];
        // initialise the array
        for(int i=0; i<intArray.length; i++) intArray[i]=0;
        // as long as there is something
        int i=0;
        while(longValue>0)
        {
            // get 8 bits
            intArray[i]=(int) (longValue & 0xFF);
            // move the other bits
            longValue >>= 8;
            i++;
        }
        // initialise the result
        String result = "";
        // assemble as string
        for(i=0; i<intArray.length; i++)
            result=result+(char) intArray[i];
        // return trimmed result
        return result.trim();
    }

    public double getValue()
    {
        //double val =  ((value-offset)/(double) divider *multiplier)/(decimals==0?1:decimals);
        double val =  (value-offset)* resolution;
        if (MainActivity.milesMode) {
            if (unit.toLowerCase().startsWith("km"))
                val = Math.round(val / 1.609344 * 10.0) / 10.0;
            else if (unit.toLowerCase().endsWith("km"))
                val = Math.round(val * 1.609344 * 10.0) / 10.0;
            //setUnit(getUnit().replace("km", "mi"));
            return val;
        }
        return val;
    }
    
    public double getMax()
    {
        double val = (int) Math.pow(2, to-from+1);
        return ((val-offset)* resolution);

    }

    public double getMin()
    {
        double val = 0;
        return ((val-offset)* resolution);
    }

    /* --------------------------------
     * Listeners management
     \ ------------------------------ */
    
    public void addListener(FieldListener fieldListener)
    {
        if(!fieldListeners.contains(fieldListener)) {
            fieldListeners.add(fieldListener);
            // trigger immediate update to pass the reference to this field
            fieldListener.onFieldUpdateEvent(this);
        }
    }
    
    public void removeListener(FieldListener fieldListener)
    {
        fieldListeners.remove(fieldListener);
    }
    
    /**
     * Notify all listeners synchronously
     */
    public void notifyFieldListeners()
    {
        notifyFieldListeners(false);
    }

    /**
     * Notify all listeners
     * @param async     true for asynchronous notifications (one thread per listener)
     */
    private void notifyFieldListeners(boolean async)
    {
        if(!async) {
            for(int i=0; i<fieldListeners.size(); i++) {
                fieldListeners.get(i).onFieldUpdateEvent(this.clone());
            }
        } else {
            // clone the frame to make sure modifications will 
            final Field clone = this.clone();
            for(int i=0; i<fieldListeners.size(); i++) {
                final int index = i;
                (new Thread(new Runnable() {

                    @Override
                    public void run() {
                        fieldListeners.get(index).onFieldUpdateEvent(clone.clone());                   
                    }
                })).start();
            }
        }
    }
    
    /* --------------------------------
     * Scheduling
    \ ------------------------------ */


    public void updateLastRequest()
    {
        lastRequest = Calendar.getInstance().getTimeInMillis();
    }

    public long getLastRequest()
    {
        return lastRequest;
    }

    public boolean isDue(long referenceTime)
    {
        return lastRequest+interval<referenceTime;
    }

    public void setInterval(int interval)
    {
        this.interval=interval;
    }

    public int getInterval()
    {
        return interval;
    }


    /* --------------------------------
     * Getters & setters
    \ ------------------------------ */

    public int getFrom() {
        return from;
    }

    public void setFrom(short from) {
        this.from = from;
    }

    public int getTo() {
        return to;
    }

    public void setTo(short to) {
        this.to = to;
    }

    public double getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public double getRawValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
        if (!Double.isNaN(value)) notifyFieldListeners();
    }

    public void setCalculatedValue(double value) {
        // inverted conversion
        if (MainActivity.milesMode)
        {
            if (getUnit().toLowerCase().startsWith("km"))
                value = value * 1.609344;
            else if (getUnit().toLowerCase().endsWith("km"))
                value = value / 1.609344;
        }
        // inverted calculation
        setValue (value / resolution + offset);
    }

    public int getId() {
        return frame.getId();
    }
    public String getHexId() {
        return Integer.toHexString(frame.getId());
    }

//    public void setId(int id) {
//        this.id = id;
//    }

    public double getResolution() {
        return resolution;
    }

    public void setResolution(double resolution) {
        this.resolution = resolution;
    }

    public String getUnit() {
        if(MainActivity.milesMode)
            return (unit+"").replace("km", "mi");
        else
            return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public int getCar() {
        return (options & 0x0f);
    }

    public boolean isCar(int car)
    {
        return (options & car)==car;
    }

    public void setCar(int car) { options = (short)((options & 0xfe0) + (car & 0x1f)); }

    public int getFrequency() {
        return frame.getInterval();
    }

    public int getDecimals() {
        return decimals;
    }

    public void setDecimals(int decimals) {
        this.decimals = decimals;
    }

    public boolean isVirtual() {
        return virtual;
    }

    public boolean isSigned () { return (this.options & 0xe0) == 1; }
}
