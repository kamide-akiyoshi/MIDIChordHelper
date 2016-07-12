package camidion.chordhelper.mididevice;

import java.awt.datatransfer.DataFlavor;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;

/**
 * {@link Receiver}のリストビュー
 */
public class ReceiverListView extends AbstractTransceiverListView<Receiver> {
	public static final DataFlavor receiverFlavor = new DataFlavor(Receiver.class,"Receiver");
	public static final DataFlavor[] receiverFlavorArray = {receiverFlavor};
	@Override
	public DataFlavor[] getElementDataFlavorArray() { return receiverFlavorArray; }
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
		setTransferHandler(cablePane.new TransceiverTransferHandler<Receiver>(this,TransmitterListView.transmitterFlavor) {
			@Override
			public boolean importData(TransferSupport support) {
				try {
					Transmitter tx = (Transmitter)support.getTransferable().getTransferData(getDestinationDataFlavor());
					tx.setReceiver(getElementAt(support.getDropLocation().getDropPoint()));
					return true;
				} catch (Exception exception) {
					exception.printStackTrace();
					return false;
				}
			}
		});
	}
}
