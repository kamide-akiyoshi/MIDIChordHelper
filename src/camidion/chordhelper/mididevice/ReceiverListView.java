package camidion.chordhelper.mididevice;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.Arrays;
import java.util.List;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.JComponent;
import javax.swing.TransferHandler;

/**
 * {@link Receiver}のリストビュー
 */
public class ReceiverListView extends AbstractTransceiverListView<Receiver> {
	public static final DataFlavor elementFlavor = new DataFlavor(Receiver.class, "Receiver");
	protected static final List<DataFlavor> flavors = Arrays.asList(elementFlavor);
	@Override
	protected String toolTipTextFor(Receiver rx) {
		return "受信端子(Rx)：ドラッグ＆ドロップしてTxに接続できます。";
	}
	@Override
	public ReceiverListModel getModel() { return (ReceiverListModel) super.getModel(); }
	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 * @param cablePane MIDIケーブル描画面
	 */
	public ReceiverListView(ReceiverListModel model, MidiCablePane cablePane) {
		super(model);
		setTransferHandler(new TransferHandler() {
			@Override
			public int getSourceActions(JComponent compo) { return COPY_OR_MOVE; }
			@Override
			protected Transferable createTransferable(JComponent compo) {
				cablePane.setDragSourceTransceiver(getSelectedValue());
				return new Transferable() {
					@Override
					public Object getTransferData(DataFlavor flavor) {
						return cablePane.getDragSourceTransceiver();
					}
					@Override
					public DataFlavor[] getTransferDataFlavors() {
						return (DataFlavor[]) flavors.toArray();
					}
					@Override
					public boolean isDataFlavorSupported(DataFlavor flavor) {
						return flavors.contains(flavor);
					}
				};
			}
			@Override
			protected void exportDone(JComponent source, Transferable data, int action) {
				cablePane.setDragSourceTransceiver(null);
			}
			@Override
			public boolean canImport(TransferSupport support) {
				if( ! support.isDrop() ) return false;
				if( support.isDataFlavorSupported(TransmitterListView.elementFlavor) ) {
					Receiver to = getElementAt(support.getDropLocation().getDropPoint());
					cablePane.setDragDestinationTransceiver(to);
					return true;
				}
				cablePane.setDragDestinationTransceiver(null);
				return false;
			}
			@Override
			public boolean importData(TransferSupport support) {
				try {
					Object from = support.getTransferable().getTransferData(TransmitterListView.elementFlavor);
					if( ! (from instanceof Transmitter) ) return false;
					Receiver to = getElementAt(support.getDropLocation().getDropPoint());
					((Transmitter)from).setReceiver(to);
					return true;
				} catch (Exception exception) {
					exception.printStackTrace();
					return false;
				}
			}
		});
	}
}
