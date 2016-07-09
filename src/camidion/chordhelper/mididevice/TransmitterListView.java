package camidion.chordhelper.mididevice;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.JComponent;

/**
 * {@link Transmitter}のリストビュー
 */
public class TransmitterListView extends AbstractTransceiverListView<Transmitter> {
	public static final DataFlavor transmitterFlavor = new DataFlavor(Transmitter.class,"Transmitter");
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
		setTransferHandler(cablePane.new TransceiverTransferHandler() {
			@Override
			protected Transferable createTransferable(JComponent compo) {
				return cablePane.new DraggingTransceiver(getSelectedValue(),transmitterFlavor);
			}
			@Override
			protected void exportDone(JComponent source, Transferable data, int action) {
				if( data != null ) {
					try {
						Transmitter tx = (Transmitter) data.getTransferData(transmitterFlavor);
						if( action == NONE ) {
							getModel().closeTransmitter(tx);
						}
						else if( tx instanceof DummyTransmitter ) {
							getModel().openTransmitter().setReceiver(tx.getReceiver());
							tx.close();
						}
					} catch (Exception exception) {
						exception.printStackTrace();
					}
				}
				super.exportDone(source, data, action);
			}
			@Override
			public boolean canImport(TransferSupport support) {
				if( ! support.isDrop() ) return false;
				if( ! support.isDataFlavorSupported(ReceiverListView.receiverFlavor) ) {
					setDestinationTransceiver(null);
					return false;
				}
				setDestinationTransceiver(getElementAt(support.getDropLocation().getDropPoint()));
				return true;
			}
			@Override
			public boolean importData(TransferSupport support) {
				try {
					Receiver rx = (Receiver)support.getTransferable().getTransferData(ReceiverListView.receiverFlavor);
					Transmitter tx = getElementAt(support.getDropLocation().getDropPoint());
					if( tx instanceof DummyTransmitter ) tx = getModel().openTransmitter();
					tx.setReceiver(rx);
					return true;
				} catch (Exception exception) {
					exception.printStackTrace();
					return false;
				}
			}
		});
	}
}
