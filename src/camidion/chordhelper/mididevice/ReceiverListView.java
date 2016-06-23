package camidion.chordhelper.mididevice;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.JList;
import javax.swing.ListSelectionModel;

/**
 * MIDIレシーバ（{@link Receiver}）のリストビューです。
 * レシーバをこのビューからドラッグし、
 * {@link TransmitterListView} のトランスミッタにドロップして接続できます。
 */
public class ReceiverListView extends JList<Receiver> {
	/**
	 * このリストによって表示される{@link Receiver}のリストを保持するデータモデルを返します。
	 * @return 表示される{@link Receiver}のリストを提供するデータモデル
	 */
	@Override
	public ReceiverListModel getModel() { return (ReceiverListModel) super.getModel(); }
	/**
	 * 引数で指定された{@link Receiver}のセル範囲を示す、
	 * リストの座標系内の境界の矩形を返します。対応するセルがない場合はnullを返します。
	 * @return セル範囲を示す境界の矩形、またはnull
	 */
	public Rectangle getCellBounds(Receiver rx) {
		int index = getModel().indexOf(rx);
		return getCellBounds(index,index);
	}
	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 * @param cablePane MIDIケーブル描画面
	 */
	public ReceiverListView(ReceiverListModel model, MidiCablePane cablePane) {
		super(model);
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setLayoutOrientation(JList.HORIZONTAL_WRAP);
		setVisibleRowCount(0);
		setCellRenderer(new TransceiverListCellRenderer<Receiver>() {
			public Component getListCellRendererComponent(JList<? extends Receiver> list,
					Receiver value, int index, boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setToolTipText("受信端子(Rx)：ドラッグ＆ドロップしてTxに接続できます。");
				return this;
			}
		});
		DragSource dragSource = new DragSource();
		DragGestureListener dgl = new DragGestureListener() {
			@Override
			public void dragGestureRecognized(DragGestureEvent event) {
				if( (event.getDragAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ) return;
				Receiver rx = getModel().getElementAt(locationToIndex(event.getDragOrigin()));
				MidiCablePane.dragging.setData(rx);
				event.startDrag(DragSource.DefaultLinkDrop, MidiCablePane.dragging, cablePane.dragSourceListener);
			}
		};
		dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, dgl);
		dragSource.addDragSourceMotionListener(cablePane.dragSourceMotionListener);
		//
		// 外からドラッグされたトランスミッタを、ドロップした場所のレシーバに接続する
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
						((Transmitter)tx).setReceiver(getModel().getElementAt(locationToIndex(event.getLocation())));
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
