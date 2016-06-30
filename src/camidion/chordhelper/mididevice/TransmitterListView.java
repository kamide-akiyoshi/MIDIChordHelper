package camidion.chordhelper.mididevice;

import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;

/**
 * {@link Transmitter}のリストビュー
 */
public class TransmitterListView extends AbstractTransceiverListView<Transmitter> {
	@Override
	public TransmitterListModel getModel() { return (TransmitterListModel) super.getModel(); }
	@Override
	protected String toolTipTextFor(Transmitter tx) {
		if( tx instanceof DummyTransmitter ) {
			return "未接続の送信端子(Tx)：ドラッグ＆ドロップしてRxに接続できます。";
		} else {
			return "接続済の送信端子(Tx)：ドラッグ＆ドロップして接続先Rxを変更、または切断できます。";
		}
	}
	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 * @param cablePane MIDIケーブル描画面
	 */
	public TransmitterListView(TransmitterListModel model, MidiCablePane cablePane) {
		super(model);
		setupDrag(cablePane);
		setupDrop();
	}
	/**
	 * {@link Transmitter}をドラッグできるようにします。
	 * @param cablePane MIDIケーブル描画面
	 */
	private void setupDrag(MidiCablePane cablePane) {
		DragSource dragSource = new DragSource();
		DragGestureListener dgl = new DragGestureListener() {
			@Override
			public void dragGestureRecognized(DragGestureEvent event) {
				if( (event.getDragAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ) return;
				MidiCablePane.dragging.setData(getElementAt(event.getDragOrigin()));
				event.startDrag(DragSource.DefaultLinkDrop, MidiCablePane.dragging, new DragSourceAdapter() {
					@Override
					public void dragDropEnd(DragSourceDropEvent event) {
						Transmitter tx = (Transmitter)MidiCablePane.dragging.getData();
						if( ! event.getDropSuccess() ) {
							getModel().closeTransmitter(tx);
						} else if( tx instanceof DummyTransmitter ) {
							Receiver rx = tx.getReceiver();
							tx.close();
							try {
								getModel().openTransmitter().setReceiver(rx);
							} catch (Exception exception) {
								exception.printStackTrace();
							}
						}
						cablePane.updateDraggingLocation(null);
					}
				});
			}
		};
		dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, dgl);
		dragSource.addDragSourceMotionListener(new DragSourceMotionListener() {
			@Override
			public void dragMouseMoved(DragSourceDragEvent dsde) {
				cablePane.updateDraggingLocation(dsde.getLocation());
			}
		});
	}
	/**
	 * {@link Receiver}のドロップを受け付けます。
	 */
	private void setupDrop() {
		DropTargetListener dtl = new DropTargetAdapter() {
			@Override
			public void dragEnter(DropTargetDragEvent event) {
				if( event.isDataFlavorSupported(DraggingTransceiver.receiverFlavor) ) {
					event.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
				}
			}
			@Override
			public void drop(DropTargetDropEvent event) {
				event.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
				if( (event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ) {
					event.dropComplete(false);
					return;
				}
				Transferable t = event.getTransferable();
				if( ! t.isDataFlavorSupported(DraggingTransceiver.receiverFlavor) ) {
					event.dropComplete(false);
					return;
				}
				try {
					Object rx = t.getTransferData(DraggingTransceiver.receiverFlavor);
					if( rx != null ) {
						Transmitter tx = getElementAt(event.getLocation());
						if( tx instanceof DummyTransmitter ) tx = getModel().openTransmitter();
						tx.setReceiver((Receiver)rx);
						event.dropComplete(true);
						return;
					}
				}
				catch (Exception ex) {
					ex.printStackTrace();
				}
				event.dropComplete(false);
			}
		};
		new DropTarget( this, DnDConstants.ACTION_COPY_OR_MOVE, dtl, true );
	}
}
