package camidion.chordhelper.mididevice;

import java.awt.Component;
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
import javax.swing.JList;

/**
 * MIDIレシーバ（{@link Receiver}）のリストビューです。
 */
public class ReceiverListView extends AbstractTransceiverListView<Receiver> {
	/**
	 * このリストによって表示される{@link Receiver}のリストを保持するデータモデルを返します。
	 * @return 表示される{@link Receiver}のリストを提供するデータモデル
	 */
	@Override
	public ReceiverListModel getModel() { return (ReceiverListModel) super.getModel(); }
	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 * @param cablePane MIDIケーブル描画面
	 */
	public ReceiverListView(ReceiverListModel model, MidiCablePane cablePane) {
		super(model);
		setCellRenderer(new TransceiverListCellRenderer<Receiver>() {
			public Component getListCellRendererComponent(JList<? extends Receiver> list,
					Receiver value, int index, boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setToolTipText("受信端子(Rx)：ドラッグ＆ドロップしてTxに接続できます。");
				return this;
			}
		});
		// ドラッグ
		DragSource dragSource = new DragSource();
		DragGestureListener dgl = new DragGestureListener() {
			@Override
			public void dragGestureRecognized(DragGestureEvent event) {
				if( (event.getDragAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ) return;
				MidiCablePane.dragging.setData(getElementAt(event.getDragOrigin()));
				event.startDrag(DragSource.DefaultLinkDrop, MidiCablePane.dragging, new DragSourceAdapter() {
					@Override
					public void dragDropEnd(DragSourceDropEvent dsde) {
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
		// ドロップ
		DropTargetListener dtl = new DropTargetAdapter() {
			@Override
			public void dragEnter(DropTargetDragEvent event) {
				if( event.isDataFlavorSupported(DraggingTransceiver.transmitterFlavor) ) {
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
				if( ! t.isDataFlavorSupported(DraggingTransceiver.transmitterFlavor) ) {
					event.dropComplete(false);
					return;
				}
				try {
					Object tx = t.getTransferData(DraggingTransceiver.transmitterFlavor);
					if( tx != null ) {
						((Transmitter)tx).setReceiver(getElementAt(event.getLocation()));
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
