package camidion.chordhelper.mididevice;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.Arrays;
import java.util.List;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;

/**
 * ドラッグ＆ドロップで転送する{@link Transmitter}または{@link Receiver}の収容箱
 */
public class DraggingTransceiver implements Transferable {
	public static final DataFlavor receiverFlavor = new DataFlavor(Receiver.class, "Receiver");
	public static final DataFlavor transmitterFlavor = new DataFlavor(Transmitter.class, "Transmitter");
	private static final List<DataFlavor> flavors = Arrays.asList(transmitterFlavor, receiverFlavor);
	private Object data;
	public Object getData() { return data; }
	public void setData(Object data) { this.data = data; }
	@Override
	public Object getTransferData(DataFlavor flavor) {
		return flavor.getRepresentationClass().isInstance(data) ? data : null;
	}
	@Override
	public DataFlavor[] getTransferDataFlavors() { return (DataFlavor[]) flavors.toArray(); }
	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) { return flavors.contains(flavor); }
}
