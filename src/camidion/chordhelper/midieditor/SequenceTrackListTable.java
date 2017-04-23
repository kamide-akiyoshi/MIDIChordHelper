package camidion.chordhelper.midieditor;

import java.awt.event.ActionEvent;
import java.util.Arrays;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellEditor;

import camidion.chordhelper.ChordHelperApplet;
import camidion.chordhelper.music.MIDISpec;

/**
 * シーケンス（トラックリスト）テーブルビュー
 */
public class SequenceTrackListTable extends JTable {
	/**
	 * トラック追加アクション
	 */
	Action addTrackAction = new AbstractAction("New") {
		{
			String tooltip = "Append new track - 新しいトラックの追加";
			putValue(Action.SHORT_DESCRIPTION, tooltip);
			setEnabled(false);
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			int newIndex = getModel().createTrack();
			selectionModel.setSelectionInterval(newIndex, newIndex);
		}
	};
	/**
	 * トラック削除アクション
	 */
	Action deleteTrackAction = new AbstractAction("Delete", MidiSequenceEditorDialog.deleteIcon) {
		public static final String CONFIRM_MESSAGE =
				"Do you want to delete selected track ?\n選択したトラックを削除しますか？";
		{
			putValue(Action.SHORT_DESCRIPTION, "Delete selected track - 選択したトラックを削除");
			setEnabled(false);
		}
		@Override
		public void actionPerformed(ActionEvent event) {
			if( JOptionPane.showConfirmDialog(
					((JComponent)event.getSource()).getRootPane(),
					CONFIRM_MESSAGE,
					ChordHelperApplet.VersionInfo.NAME,
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION
			) getModel().deleteSelectedTracks(selectionModel);
		}
	};
	/**
	 * トラックリストテーブルビューを構築します。
	 * @param model シーケンス（トラックリスト）データモデル
	 * @param eventListTable イベントリストテーブル
	 */
	public SequenceTrackListTable(SequenceTrackListTableModel model, MidiEventTable eventListTable) {
		super(model, null, model.getSelectionModel());
		getColumnModel()
			.getColumn(SequenceTrackListTableModel.Column.RECORD_CHANNEL.ordinal())
			.setCellEditor(new DefaultCellEditor(new JComboBox<String>(){{
				addItem("OFF");
				for(int i=1; i <= MIDISpec.MAX_CHANNELS; i++) addItem(String.format("%d", i));
				addItem("ALL");
			}}));
		setAutoCreateColumnsFromModel(false);
		Arrays.stream(SequenceTrackListTableModel.Column.values()).forEach(c->
			getColumnModel().getColumn(c.ordinal()).setPreferredWidth(c.preferredWidth)
		);
		selectionModel.addListSelectionListener(selectionListener = event->{
			if( event.getValueIsAdjusting() ) return;
			deleteTrackAction.setEnabled(! selectionModel.isSelectionEmpty());
			eventListTable.setModel(getModel().getSelectedTrackModel());
		});
	}
	/**
	 * トラック選択リスナー
	 */
	private ListSelectionListener selectionListener;
	/**
	 * このテーブルビューが表示するデータを提供するシーケンス（トラックリスト）データモデルを返します。
	 * @return シーケンス（トラックリスト）データモデル
	 */
	@Override
	public SequenceTrackListTableModel getModel() {
		return (SequenceTrackListTableModel)dataModel;
	}
	/**
	 * このテーブルビューが表示するデータを提供するシーケンス（トラックリスト）データモデルを設定します。
	 * @param model シーケンス（トラックリスト）データモデル
	 */
	public void setModel(SequenceTrackListTableModel model) {
		if( dataModel == model ) return;
		cancelCellEditing();
		if( model == null ) {
			model = getModel().getParent().emptyTrackListTableModel;
			addTrackAction.setEnabled(false);
		}
		else {
			addTrackAction.setEnabled(true);
		}
		selectionModel.clearSelection();
		selectionModel.removeListSelectionListener(selectionListener);
		super.setModel(model);
		setSelectionModel(model.getSelectionModel());
		titleLabel.setSelection(model.getParent().getSelectionModel());
		selectionModel.addListSelectionListener(selectionListener);
	}
	/**
	 * 曲番号表示付きタイトルラベル
	 */
	TitleLabel titleLabel = new TitleLabel();
	/**
	 * 曲番号表示付きタイトルラベル
	 */
	private class TitleLabel extends JLabel {
		private static final String TITLE = "Tracks";
		public TitleLabel() { setText(TITLE); }
		public void setSelection(ListSelectionModel sequenceSelectionModel) {
			String text = TITLE;
			if( ! sequenceSelectionModel.isSelectionEmpty() ) {
				int index = sequenceSelectionModel.getMinSelectionIndex();
				if( index >= 0 ) text = String.format(text+" - MIDI file #%d", index);
			}
			setText(text);
		}
	}
	/**
	 * {@inheritDoc}
	 *
	 * <p>このトラックリストテーブルのデータが変わったときに編集を解除します。
	 * 例えば、イベントが編集された場合や、シーケンサーからこのモデルが外された場合がこれに該当します。
	 * </p>
	 */
	@Override
	public void tableChanged(TableModelEvent e) {
		super.tableChanged(e);
		cancelCellEditing();
	}
	/**
	 * このトラックリストテーブルが編集モードになっていたら解除します。
	 */
	private void cancelCellEditing() {
		TableCellEditor currentCellEditor = getCellEditor();
		if( currentCellEditor != null ) currentCellEditor.cancelCellEditing();
	}
}
