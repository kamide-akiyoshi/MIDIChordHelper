package camidion.chordhelper.mididevice;

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import camidion.chordhelper.ButtonIcon;

/**
 * Transmitter(Tx)/Receiver(Rx) のリスト（view）
 *
 * <p>マウスで Tx からドラッグして Rx へドロップする機能を備えた
 * 仮想MIDI端子リストです。
 * </p>
 */
public class MidiConnecterListView extends JList<AutoCloseable>
	implements Transferable, DragGestureListener, DropTargetListener
{
	public static final Icon MIDI_CONNECTER_ICON =
		new ButtonIcon(ButtonIcon.MIDI_CONNECTOR_ICON);
	private class CellRenderer extends JLabel implements ListCellRenderer<AutoCloseable> {
		public Component getListCellRendererComponent(
			JList<? extends AutoCloseable> list,
			AutoCloseable value,
			int index,
			boolean isSelected,
			boolean cellHasFocus
		) {
			String text;
			if( value instanceof Transmitter ) text = "Tx";
			else if( value instanceof Receiver ) text = "Rx";
			else text = (value==null ? null : value.toString());
			setText(text);
			setIcon(MIDI_CONNECTER_ICON);
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			setEnabled(list.isEnabled());
			setFont(list.getFont());
			setOpaque(true);
			return this;
		}
	}
	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 */
	public MidiConnecterListView(MidiConnecterListModel model) {
		super(model);
		setCellRenderer(new CellRenderer());
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setLayoutOrientation(JList.HORIZONTAL_WRAP);
		setVisibleRowCount(0);
        (new DragSource()).createDefaultDragGestureRecognizer(
        	this, DnDConstants.ACTION_COPY_OR_MOVE, this
        );
		new DropTarget( this, DnDConstants.ACTION_COPY_OR_MOVE, this, true );
	}
	private static final DataFlavor transmitterFlavor =
		new DataFlavor(Transmitter.class, "Transmitter");
	private static final DataFlavor transmitterFlavors[] = {transmitterFlavor};
	@Override
	public Object getTransferData(DataFlavor flavor) {
		return getModel().getElementAt(getSelectedIndex());
	}
	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return transmitterFlavors;
	}
	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return flavor.equals(transmitterFlavor);
	}
	@Override
	public void dragGestureRecognized(DragGestureEvent dge) {
		int action = dge.getDragAction();
		if( (action & DnDConstants.ACTION_COPY_OR_MOVE) == 0 )
			return;
		int index = locationToIndex(dge.getDragOrigin());
		AutoCloseable data = getModel().getElementAt(index);
		if( data instanceof Transmitter ) {
			dge.startDrag(DragSource.DefaultLinkDrop, this, null);
		}
	}
	@Override
	public void dragEnter(DropTargetDragEvent event) {
		if( event.isDataFlavorSupported(transmitterFlavor) )
			event.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
	}
	@Override
	public void dragExit(DropTargetEvent dte) {}
	@Override
	public void dragOver(DropTargetDragEvent dtde) {}
	@Override
	public void dropActionChanged(DropTargetDragEvent dtde) {}
	@Override
	public void drop(DropTargetDropEvent event) {
		event.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
		try {
			int maskedBits = event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE;
			if( maskedBits != 0 ) {
				Transferable t = event.getTransferable();
				Object data = t.getTransferData(transmitterFlavor);
				if( data instanceof Transmitter ) {
					getModel().ConnectToReceiver((Transmitter)data);
					event.dropComplete(true);
					return;
				}
			}
			event.dropComplete(false);
		}
		catch (Exception ex) {
			ex.printStackTrace();
			event.dropComplete(false);
		}
	}
	@Override
	public MidiConnecterListModel getModel() {
		return (MidiConnecterListModel)super.getModel();
	}
	/**
	 * 選択されているトランスミッタを閉じます。
	 * レシーバが選択されていた場合は無視されます。
	 */
	public void closeSelectedTransmitter() {
		AutoCloseable ac = getSelectedValue();
		if( ac instanceof Transmitter )
			getModel().closeTransmitter((Transmitter)ac);
	}
}