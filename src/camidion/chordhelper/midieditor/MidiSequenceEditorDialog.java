package camidion.chordhelper.midieditor;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.sound.midi.Sequencer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumnModel;

import camidion.chordhelper.ButtonIcon;
import camidion.chordhelper.ChordHelperApplet;
import camidion.chordhelper.mididevice.MidiSequencerModel;
import camidion.chordhelper.mididevice.VirtualMidiDevice;
import camidion.chordhelper.music.MIDISpec;

/**
 * MIDIエディタ（MIDI Editor/Playlist for MIDI Chord Helper）
 *
 * @author
 *	Copyright (C) 2006-2016 Akiyoshi Kamide
 *	http://www.yk.rim.or.jp/~kamide/music/chordhelper/
 */
public class MidiSequenceEditorDialog extends JDialog {
	/**
	 * このダイアログを開きます。表示されていなければ表示し、すでに表示されていたら最前面に移動します。
	 */
	public void open() { if( isVisible() ) toFront(); else setVisible(true); }
	/**
	 * このダイアログを表示するボタン用アクション
	 */
	public final Action openAction = new AbstractAction("Edit/Playlist/Speed", new ButtonIcon(ButtonIcon.EDIT_ICON)) {
		{
			String tooltip = "MIDIシーケンスの編集／プレイリスト／再生速度調整";
			putValue(Action.SHORT_DESCRIPTION, tooltip);
		}
		@Override
		public void actionPerformed(ActionEvent e) { open(); }
	};
	/**
	 * ドロップされた複数のMIDIファイルを読み込むハンドラー
	 */
	public final TransferHandler transferHandler = new TransferHandler() {
		@Override
		public boolean canImport(TransferSupport support) {
			return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
		}
		@SuppressWarnings("unchecked")
		@Override
		public boolean importData(TransferSupport support) {
			try {
				Object data = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
				play((List<File>)data);
				return true;
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(
						MidiSequenceEditorDialog.this, ex,
						ChordHelperApplet.VersionInfo.NAME,
						JOptionPane.ERROR_MESSAGE);
				return false;
			}
		}
	};
	/**
	 * このエディタダイアログが表示しているプレイリストモデルを返します。
	 * @return プレイリストモデル
	 */
	public PlaylistTableModel getPlaylistModel() {
		return sequenceListTable.getModel();
	}
	/**
	 * 指定されたリストに格納されたMIDIファイルを読み込んで再生します。
	 * すでに再生されていた場合、このエディタダイアログを表示します。
	 * @param fileList 読み込むMIDIファイルのリスト
	 */
	public void play(List<File> fileList) {
		play(sequenceListTable.add(fileList));
	}
	/**
	 * 指定されたインデックス値（先頭が0）のMIDIシーケンスから再生します。
	 * すでに再生されていた場合、このエディタダイアログを表示します。
	 * @param index プレイリスト内にあるMIDIシーケンスのインデックス値
	 */
	public void play(int index) {
		try {
			PlaylistTableModel playlist = getPlaylistModel();
			MidiSequencerModel sequencerModel = playlist.getSequencerModel();
			if( sequencerModel.getSequencer().isRunning() ) { open(); return; }
			if( index >= 0 ) playlist.play(index);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, e, ChordHelperApplet.VersionInfo.NAME, JOptionPane.ERROR_MESSAGE);
		}
	}

	static final Icon deleteIcon = new ButtonIcon(ButtonIcon.X_ICON);
	/**
	 * 新しいMIDIシーケンスを生成するダイアログ
	 */
	public NewSequenceDialog newSequenceDialog;
	/**
	 * プレイリストビュー（シーケンスリスト）
	 */
	public PlaylistTable sequenceListTable;
	/**
	 * MIDIトラックリストテーブルビュー（選択中のシーケンスの中身）
	 */
	private SequenceTrackListTable trackListTable;
	/**
	 * MIDIイベントリストテーブルビュー（選択中のトラックの中身）
	 */
	public MidiEventTable eventListTable;
	/**
	 * シーケンス（トラックリスト）テーブルビュー
	 */
	public class SequenceTrackListTable extends JTable {
		/**
		 * トラックリストテーブルビューを構築します。
		 * @param model シーケンス（トラックリスト）データモデル
		 */
		public SequenceTrackListTable(SequenceTrackListTableModel model) {
			super(model, null, model.getSelectionModel());
			//
			// 録音対象のMIDIチャンネルをコンボボックスで選択できるようにする
			getColumnModel()
				.getColumn(SequenceTrackListTableModel.Column.RECORD_CHANNEL.ordinal())
				.setCellEditor(new DefaultCellEditor(new JComboBox<String>(){{
					addItem("OFF");
					for(int i=1; i <= MIDISpec.MAX_CHANNELS; i++) addItem(String.format("%d", i));
					addItem("ALL");
				}}));
			setAutoCreateColumnsFromModel(false);
			model.getParent().sequenceListSelectionModel.addListSelectionListener(titleLabel);
			TableColumnModel colModel = getColumnModel();
			Arrays.stream(SequenceTrackListTableModel.Column.values()).forEach(c->
				colModel.getColumn(c.ordinal()).setPreferredWidth(c.preferredWidth)
			);
		}
		/**
		 * このテーブルビューが表示するデータを提供する
		 * シーケンス（トラックリスト）データモデルを返します。
		 * @return シーケンス（トラックリスト）データモデル
		 */
		@Override
		public SequenceTrackListTableModel getModel() {
			return (SequenceTrackListTableModel)dataModel;
		}
		/**
		 * タイトルラベル
		 */
		TitleLabel titleLabel = new TitleLabel();
		/**
		 * 親テーブルの選択シーケンスの変更に反応する
		 * 曲番号表示付きタイトルラベル
		 */
		private class TitleLabel extends JLabel implements ListSelectionListener {
			private static final String TITLE = "Tracks";
			public TitleLabel() { setText(TITLE); }
			@Override
			public void valueChanged(ListSelectionEvent event) {
				if( event.getValueIsAdjusting() ) return;
				SequenceTrackListTableModel oldModel = getModel();
				SequenceTrackListTableModel newModel = oldModel.getParent().getSelectedSequenceModel();
				if( oldModel == newModel ) return;
				//
				// MIDIチャンネル選択中のときはキャンセルする
				cancelCellEditing();
				//
				String text = TITLE;
				ListSelectionModel sm = oldModel.getParent().sequenceListSelectionModel;
				if( ! sm.isSelectionEmpty() ) {
					int index = sm.getMinSelectionIndex();
					if( index >= 0 ) text = String.format(text+" - MIDI file #%d", index);
				}
				setText(text);
				if( newModel == null ) {
					newModel = oldModel.getParent().emptyTrackListTableModel;
					addTrackAction.setEnabled(false);
				}
				else {
					addTrackAction.setEnabled(true);
				}
				oldModel.getSelectionModel().removeListSelectionListener(trackSelectionListener);
				setModel(newModel);
				setSelectionModel(newModel.getSelectionModel());
				newModel.getSelectionModel().addListSelectionListener(trackSelectionListener);
				trackSelectionListener.valueChanged(null);
			}
		}
		/**
		 * トラック選択リスナー
		 */
		ListSelectionListener trackSelectionListener = new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if( e != null && e.getValueIsAdjusting() ) return;
				ListSelectionModel selModel = getModel().getSelectionModel();
				deleteTrackAction.setEnabled(! selModel.isSelectionEmpty());
				eventListTable.updateTo(getModel());
			}
		};
		/**
		 * {@inheritDoc}
		 *
		 * <p>このトラックリストテーブルのデータが変わったときに編集を解除します。
		 * 例えば、イベントが編集された場合や、
		 * シーケンサーからこのモデルが外された場合がこれに該当します。
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
			public void actionPerformed(ActionEvent e) { getModel().createTrack(); }
		};
		/**
		 * トラック削除アクション
		 */
		Action deleteTrackAction = new AbstractAction("Delete", deleteIcon) {
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
				) getModel().deleteSelectedTracks();
			}
		};
	}

	/**
	 * 新しい {@link MidiSequenceEditorDialog} を構築します。
	 * @param playlistTableModel このエディタが参照するプレイリストモデル
	 * @param outputMidiDevice イベントテーブルの操作音出力先MIDIデバイス
	 * @param midiDeviceDialogOpenAction MIDIデバイスダイアログを開くアクション
	 */
	public MidiSequenceEditorDialog(PlaylistTableModel playlistTableModel, VirtualMidiDevice outputMidiDevice, Action midiDeviceDialogOpenAction) {
		sequenceListTable = new PlaylistTable(playlistTableModel, midiDeviceDialogOpenAction);
		trackListTable = new SequenceTrackListTable(playlistTableModel.emptyTrackListTableModel);
		eventListTable = new MidiEventTable(playlistTableModel.emptyEventListTableModel, outputMidiDevice);
		newSequenceDialog = new NewSequenceDialog(playlistTableModel, outputMidiDevice);
		setTitle("MIDI Editor/Playlist - "+ChordHelperApplet.VersionInfo.NAME);
		setBounds( 150, 200, 900, 500 );
		setLayout(new FlowLayout());
		setTransferHandler(transferHandler);
		//
		// パネルレイアウト
		JPanel playlistPanel = new JPanel() {{
			JPanel playlistOperationPanel = new JPanel() {{
				setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
				add(Box.createRigidArea(new Dimension(10, 0)));
				add(new JButton(newSequenceDialog.openAction) {
					{ setMargin(ChordHelperApplet.ZERO_INSETS); }
				});
				if( sequenceListTable.midiFileChooser != null ) {
					add( Box.createRigidArea(new Dimension(5, 0)) );
					add(new JButton(sequenceListTable.midiFileChooser.openMidiFileAction) {
						{ setMargin(ChordHelperApplet.ZERO_INSETS); }
					});
				}
				if(sequenceListTable.base64EncodeAction != null) {
					add(Box.createRigidArea(new Dimension(5, 0)));
					add(new JButton(sequenceListTable.base64EncodeAction) {
						{ setMargin(ChordHelperApplet.ZERO_INSETS); }
					});
				}
				add(Box.createRigidArea(new Dimension(5, 0)));
				add(new JButton(playlistTableModel.getMoveToTopAction()) {
					{ setMargin(ChordHelperApplet.ZERO_INSETS); }
				});
				add(Box.createRigidArea(new Dimension(5, 0)));
				add(new JButton(playlistTableModel.getMoveToBottomAction()) {
					{ setMargin(ChordHelperApplet.ZERO_INSETS); }
				});
				if( sequenceListTable.midiFileChooser != null ) {
					add(Box.createRigidArea(new Dimension(5, 0)));
					add(new JButton(sequenceListTable.midiFileChooser.saveMidiFileAction) {
						{ setMargin(ChordHelperApplet.ZERO_INSETS); }
					});
				}
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add(new JButton(sequenceListTable.deleteSequenceAction) {
					{ setMargin(ChordHelperApplet.ZERO_INSETS); }
				});
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add(new SequencerSpeedSlider(playlistTableModel.getSequencerModel().speedSliderModel));
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add(new JPanel() {{
					setBorder(new EtchedBorder());
					MidiSequencerModel sequencerModel = playlistTableModel.getSequencerModel();
					add(new JLabel("Sync Master"));
					add(new JComboBox<Sequencer.SyncMode>(sequencerModel.masterSyncModeModel));
					add(new JLabel("Slave"));
					add(new JComboBox<Sequencer.SyncMode>(sequencerModel.slaveSyncModeModel));
				}});
			}};
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			add(new JScrollPane(sequenceListTable));
			add(Box.createRigidArea(new Dimension(0, 10)));
			add(playlistOperationPanel);
			add(Box.createRigidArea(new Dimension(0, 10)));
		}};
		JPanel trackListPanel = new JPanel() {{
			JPanel trackListOperationPanel = new JPanel() {{
				add(new JButton(trackListTable.addTrackAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
				add(new JButton(trackListTable.deleteTrackAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
			}};
			setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
			add(trackListTable.titleLabel);
			add(Box.createRigidArea(new Dimension(0, 5)));
			add(new JScrollPane(trackListTable));
			add(Box.createRigidArea(new Dimension(0, 5)));
			add(trackListOperationPanel);
		}};
		JPanel eventListPanel = new JPanel() {{
			JPanel eventListOperationPanel = new JPanel() {{
				add(new JCheckBox("Pair NoteON/OFF") {{
					setModel(eventListTable.pairNoteOnOffModel);
					setToolTipText("NoteON/OFFをペアで同時選択する");
				}});
				add(new JButton(eventListTable.queryJumpEventAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
				add(new JButton(eventListTable.queryAddEventAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
				add(new JButton(eventListTable.copyEventAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
				add(new JButton(eventListTable.cutEventAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
				add(new JButton(eventListTable.queryPasteEventAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
				add(new JButton(eventListTable.deleteEventAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
			}};
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			add(eventListTable.titleLabel);
			add(eventListTable.scrollPane);
			add(eventListOperationPanel);
		}};
		Container cp = getContentPane();
		cp.setLayout(new BoxLayout(cp, BoxLayout.Y_AXIS));
		cp.add(Box.createVerticalStrut(2));
		cp.add(
			new JSplitPane(JSplitPane.VERTICAL_SPLIT, playlistPanel,
				new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, trackListPanel, eventListPanel) {{
					setDividerLocation(300);
				}}
			) {{
				setDividerLocation(160);
			}}
		);
	}

}
