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
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

/**
 * MIDIレシーバ（{@link Receiver}）のリストビューです。
 * レシーバをこのビューからドラッグし、
 * {@link TransmitterListView} のトランスミッタにドロップして接続できます。
 */
public class ReceiverListView extends JList<Receiver> {
	/**
	 * レシーバを描画するクラス
	 */
	private static class CellRenderer extends JLabel implements ListCellRenderer<Receiver> {
		public Component getListCellRendererComponent(JList<? extends Receiver> list,
				Receiver value, int index, boolean isSelected, boolean cellHasFocus)
		{
			setEnabled(list.isEnabled());
			setFont(list.getFont());
			setOpaque(true);
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			setIcon(MidiDeviceDialog.MIDI_CONNECTER_ICON);
			setToolTipText("受信端子(Rx)：ドラッグ＆ドロップしてTxに接続できます。");
			return this;
		}
	}
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
	 * このリストによって表示される{@link Receiver}のリストを保持するデータモデルを返します。
	 * @return 表示される{@link Receiver}のリストを提供するデータモデル
	 */
	@Override
	public ReceiverListModel getModel() {
		return (ReceiverListModel) super.getModel();
	}
	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 * @param cablePane MIDIケーブル描画面
	 */
	public ReceiverListView(ReceiverListModel model, MidiCablePane cablePane) {
		super(model);
		setCellRenderer(new CellRenderer());
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setLayoutOrientation(JList.HORIZONTAL_WRAP);
		setVisibleRowCount(0);
		//
		// レシーバのドラッグを受け付ける
		DragGestureListener dgl = new DragGestureListener() {
			@Override
			public void dragGestureRecognized(DragGestureEvent event) {
				if( (event.getDragAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ) return;
				MidiCablePane.dragging.setData(getModel().getElementAt(locationToIndex(event.getDragOrigin())));
				event.startDrag(DragSource.DefaultLinkDrop, MidiCablePane.dragging, cablePane.dragSourceListener);
			}
		};
		DragSource dragSource = new DragSource();
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
				int maskedBits = event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE;
				if( maskedBits == 0 ) {
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
