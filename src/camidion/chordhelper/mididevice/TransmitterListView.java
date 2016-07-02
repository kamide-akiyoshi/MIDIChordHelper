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
 * {@link Transmitter}のリストビュー
 */
public class TransmitterListView extends AbstractTransceiverListView<Transmitter> {
	public static final DataFlavor elementFlavor = new DataFlavor(Transmitter.class, "Transmitter");
	protected static final List<DataFlavor> flavors = Arrays.asList(elementFlavor);
	@Override
	protected String toolTipTextFor(Transmitter tx) {
		if( tx instanceof DummyTransmitter ) {
			return "未接続の送信端子(Tx)：ドラッグ＆ドロップしてRxに接続できます。";
		} else {
			return "接続済の送信端子(Tx)：ドラッグ＆ドロップして接続先Rxを変更、または切断できます。";
		}
	}
	@Override
	public TransmitterListModel getModel() { return (TransmitterListModel) super.getModel(); }
	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 * @param cablePane MIDIケーブル描画面
	 */
	public TransmitterListView(TransmitterListModel model, MidiCablePane cablePane) {
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
				try {
					Transmitter tx = (Transmitter) data.getTransferData(elementFlavor);
					if( action == NONE ) {
						getModel().closeTransmitter(tx);
					}
					else if( tx instanceof DummyTransmitter ) {
						Receiver rx = tx.getReceiver();
						tx.close();
						getModel().openTransmitter().setReceiver(rx);
					}
				} catch (Exception exception) {
					exception.printStackTrace();
				}
				cablePane.setDragSourceTransceiver(null);
			}
			@Override
			public boolean canImport(TransferSupport support) {
				if( ! support.isDrop() ) return false;
				if( support.isDataFlavorSupported(ReceiverListView.elementFlavor) ) {
					Transmitter to = getElementAt(support.getDropLocation().getDropPoint());
					cablePane.setDragDestinationTransceiver(to);
					return true;
				}
				cablePane.setDragDestinationTransceiver(null);
				return false;
			}
			@Override
			public boolean importData(TransferSupport support) {
				try {
					Object from = support.getTransferable().getTransferData(ReceiverListView.elementFlavor);
					if( ! (from instanceof Receiver) ) return false;
					Transmitter to = getElementAt(support.getDropLocation().getDropPoint());
					if( to instanceof DummyTransmitter ) to = getModel().openTransmitter();
					to.setReceiver((Receiver)from);
					return true;
				} catch (Exception exception) {
					exception.printStackTrace();
					return false;
				}
			}
		});
	}
}
