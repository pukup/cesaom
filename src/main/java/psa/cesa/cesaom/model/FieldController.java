package psa.cesa.cesaom.model;

import psa.cesa.cesaom.controller.SerialController;
import psa.cesa.cesaom.model.dao.ComLine;
import psa.cesa.cesaom.model.dao.Heliostat;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * It polls and commands <code>Heliostat</code> objects within a <code>ComLine<code/>.
 */
public class FieldController {
    /**
     * @param POLL_ARRAY Contents the bytes to send a poll request on any heliostat.
     * The address byte must be added by <method>poll</method> method.
     * @param rows contents a map filled with all the <code>ComLine</code> objects of the xml file.
     * @param comLine <code>ComLine</code> object.
     * @param heliostat <code>Heliostat</code> object.
     * @param serialController contains the methods to control the jSerialComm API.
     */
    private static final byte[] POLL_ARRAY = {0x03, 0x00, 0x10, 0x00, 0x08};
    private static final byte[] HOUR_ARRAY = {0x03, 0x03, (byte) 0xEB, 0x00, 0x02};
    private Map<Integer, ComLine> comLines;
    private ComLine comLine;
    private Heliostat heliostat;
    private SerialController serialController;

    public FieldController(Map<Integer, ComLine> comLines) {
        this.comLines = comLines;
    }

    public Map<Integer, ComLine> getComLines() {
        return comLines;
    }

    public void setComLines(Map<Integer, ComLine> comLines) {
        this.comLines = comLines;
    }

    /**
     * It targets a <code>ComLine</code> and an <code>Heliostat</code> to send and receive the poll bytes from it.
     *
     * @param comLineId        represents the number or position of the comLine.
     * @param heliostatAddress represents the modbus slave address.
     */
    public Heliostat poll(int comLineId, int heliostatAddress) throws Exception {
        selectHeliostat(comLineId, heliostatAddress);
        serialController.open();
        sendPollerArray();
        Thread.sleep(100);
        checkPollResponse();
        return heliostat;
    }

    /**
     * Adds the <code>Heliostat</code> address and the poller bytes to a buffer and sends it.
     */
    private void sendPollerArray() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(8);
        byteBuffer.put((byte) heliostat.getId());
        byteBuffer.put(POLL_ARRAY);
        byteBuffer.put(CRC.calculate(byteBuffer.array(), 6));
        serialController.send(byteBuffer.array());
    }

    /**
     * Checks the received bytes and uses <method>setHelioState</method> to set the <code>Heliostat</code> attributes.
     */
    private Heliostat checkPollResponse() {
        if (serialController.getPort().bytesAvailable() < 1) {
            heliostat.setEvent(0x10);
//            throw new RuntimeException("El heliostato " + heliostat.getAddress() + " no responde al poll");
        } else {
            ByteBuffer receivedBuffer = ByteBuffer.wrap(serialController.receive());
            heliostat.setAttributes(receivedBuffer);
            comLine.getHeliostats().put(heliostat.getId(), heliostat);
        }
        serialController.close();
        return heliostat;
    }

    /**
     * Checks the received bytes and put them into a string.
     *
     * @param comLineId
     * @param heliostatAddress
     * @throws InterruptedException
     */
    public String printReceivedBuffer(int comLineId, int heliostatAddress) throws InterruptedException {
        StringBuffer s = new StringBuffer("| ");
        selectHeliostat(comLineId, heliostatAddress);
        serialController.open();
        sendPollerArray();
        Thread.sleep(100);
        if (serialController.getPort().bytesAvailable() < 1) {
            s.append("No contesta");
        } else {
            ByteBuffer receivedBuffer = ByteBuffer.wrap(serialController.receive());
            for (byte b : receivedBuffer.array()) {
                s.append(String.format("%02x ", b));
            }
        }
        serialController.close();
        return s.toString();
    }

    /**
     * It targets a <code>ComLine</code> and an <code>Heliostat</code> to send commands.
     *
     * @param comLineId
     * @param heliostatAddress
     * @param command
     */
    public boolean command(int comLineId, int heliostatAddress, String command) throws InterruptedException {
        selectHeliostat(comLineId, heliostatAddress);
        serialController.open();
        sendCommandArray(command);
        Thread.sleep(100);
        boolean b = checkCommandResponse();
        serialController.close();
        return b;
    }

    /**
     * Adds the <code>Heliostat</code> address and the command bytes to a buffer and sends it.
     *
     * @param command ASCII representation to switch between different commands.
     */
    private void sendCommandArray(String command) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(11);
        byteBuffer.put((byte) heliostat.getId());
        byteBuffer.put(selectCommand(command));
        byteBuffer.put(CRC.calculate(byteBuffer.array(), 9));
        serialController.send(byteBuffer.array());
    }

    /**
     * It adds modbus function code 16 byte, <code>ComLine</code> id, <code>Heliostat</code> id, and one ASCII command selected from the switch case.
     * It adds switches between commands with no extra parameters needed.
     *
     * @param command the ASCII value of the command.
     * @return byte array with the function and the command.
     */
    private byte[] selectCommand(String command) {
        byte[] bytes = new byte[]{16, 0, 0, 0, 1, 2, 0, 0};
        switch (command) {
            case "a":
                bytes[7] = 97;
                break;
            case "b":
                bytes[7] = 102;
                break;
            case "d":
                bytes[7] = 100;
                break;
            case "e":
                bytes[7] = 101;
                break;
            case "i":
                bytes[7] = 105;
                break;
            case "l":
                bytes[7] = 108;
                break;
            case "n":
                bytes[7] = 110;
                break;
            case "q":
                bytes[7] = 113;
                break;
            case "s":
                bytes[7] = 115;
                break;
        }
        return bytes;
    }

    //TO DO: setHour in the whole field

    /**
     * @param comLineID
     * @param heliostatAddress
     * @throws InterruptedException
     */
    public void getHour(int comLineID, int heliostatAddress) throws InterruptedException {
        selectHeliostat(comLineID, heliostatAddress);
        serialController.open();
        sendHourFrame();
        Thread.sleep(100);
        if (serialController.getPort().bytesAvailable() < 1) {
            heliostat.setEvent(0x10);
            //            throw new RuntimeException("El heliostato no responde al poll");
        } else {
            ByteBuffer receivedBuffer = ByteBuffer.wrap(serialController.receive());
            for (byte bits : receivedBuffer.array()) {
                System.out.format("0x%h ", bits);
            }
        }
        serialController.close();
    }

    /**
     * It targets a <code>ComLine</code> and an <code>Heliostat</code> to ask 3 bytes from the 218 address which keeps hour
     */
    private void sendHourFrame() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(8);
        byteBuffer.put((byte) heliostat.getId());
        byteBuffer.put(HOUR_ARRAY);
        byteBuffer.put(CRC.calculate(byteBuffer.array(), 6));
        serialController.send(byteBuffer.array());
    }

    /**
     * @param comLineId
     * @param heliostatAddress
     * @param n
     * @return
     * @throws InterruptedException
     */
    public boolean sendFocus(int comLineId, int heliostatAddress, int n) throws InterruptedException {
        selectHeliostat(comLineId, heliostatAddress);
        serialController.open();
        sendFocusArray(n);
        Thread.sleep(100);
        boolean bool = checkCommandResponse();
        serialController.close();
        return bool;
    }

    /**
     * @param n
     */
    private void sendFocusArray(int n) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(0);
        byteBuffer.put((byte) heliostat.getId());
        //                        byteBuffer.put();
        //        CRC.calculate(byteBuffer.array());
        serialController.send(byteBuffer.array());
    }

    /**
     * @param rowId
     * @param heliostatAddress
     * @param x
     * @param y
     * @param z
     */
    public void sendFocus(int rowId, int heliostatAddress, long x, long y, long z) {
    }

    /**
     * @param rowId
     * @param heliostatAddress
     * @param az
     * @param el
     */
    public void sendPosition(int rowId, int heliostatAddress, int az, int el) {
    }

    /**
     * @param rowId
     * @param heliostatAddress
     */
    private void selectHeliostat(int rowId, int heliostatAddress) {
        comLine = comLines.get(rowId);
        heliostat = comLine.getHeliostats().get(heliostatAddress);
        serialController = new SerialController(comLine.getPortDir());
    }

    /**
     * @return
     */
    private boolean checkCommandResponse() {
        if (serialController.getPort().bytesAvailable() < 1) {
            heliostat.setEvent(0x10);
            throw new RuntimeException("El heliostato no responde al comando");
        } else {
            return true;
        }
    }
}
