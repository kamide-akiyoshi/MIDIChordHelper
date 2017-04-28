package camidion.chordhelper.midieditor;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;

import javax.sound.midi.Sequencer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.TransferHandler;
import javax.swing.border.EtchedBorder;

import camidion.chordhelper.ButtonIcon;
import camidion.chordhelper.ChordHelperApplet;
import camidion.chordhelper.mididevice.MidiSequencerModel;
import camidion.chordhelper.mididevice.VirtualMidiDevice;

/**
 * MIDIエディタ（MIDI Editor/Playlist for MIDI Chord Helper）
 *
 * @author
 *	Copyright (C) 2006-2017 Akiyoshi Kamide
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
	 * プレイリストビュー（シーケンスリスト）
	 */
	public PlaylistTable playlistTable;
	/**
	 * このエディタダイアログが表示しているプレイリストモデルを返します。
	 * @return プレイリストモデル
	 */
	public PlaylistTableModel getPlaylistModel() {
		return playlistTable.getModel();
	}
	/**
	 * 指定されたリストに格納されたMIDIファイルを読み込んで再生します。
	 * すでに再生されていた場合、このエディタダイアログを表示します。
	 * @param fileList 読み込むMIDIファイルのリスト
	 */
	public void play(List<File> fileList) {
		play(playlistTable.add(fileList));
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
			if( index >= 0 ) {
				playlist.play(index);
				playlistTable.getSelectionModel().setSelectionInterval(index, index);
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, e, ChordHelperApplet.VersionInfo.NAME, JOptionPane.ERROR_MESSAGE);
		}
	}
	/**
	 * 現在選択されているMIDIシーケンスから再生します。
	 * すでに再生されていた場合、このエディタダイアログを表示します。
	 */
	public void play() {
		play(playlistTable.getSelectionModel().getMinSelectionIndex());
	}

	static final Icon deleteIcon = new ButtonIcon(ButtonIcon.X_ICON);
	/**
	 * 新しいMIDIシーケンスを生成するダイアログ
	 */
	public NewSequenceDialog newSequenceDialog;
	/**
	 * 新しい {@link MidiSequenceEditorDialog} を構築します。
	 * @param playlistTableModel このエディタが参照するプレイリストモデル
	 * @param eventDialog MIDIイベント入力ダイアログ
	 * @param outputMidiDevice イベントテーブルの操作音出力先MIDIデバイス
	 * @param midiDeviceDialogOpenAction MIDIデバイスダイアログを開くアクション
	 */
	public MidiSequenceEditorDialog(PlaylistTableModel playlistTableModel, MidiEventDialog eventDialog, VirtualMidiDevice outputMidiDevice, Action midiDeviceDialogOpenAction) {
		MidiEventTable eventListTable = new MidiEventTable(playlistTableModel.emptyEventListTableModel, eventDialog, outputMidiDevice);
		SequenceTrackListTable trackListTable = new SequenceTrackListTable(playlistTableModel.emptyTrackListTableModel, eventListTable);
		playlistTable = new PlaylistTable(playlistTableModel, midiDeviceDialogOpenAction, trackListTable);
		newSequenceDialog = new NewSequenceDialog(playlistTable, outputMidiDevice);
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
				if( playlistTable.midiFileChooser != null ) {
					add( Box.createRigidArea(new Dimension(5, 0)) );
					add(new JButton(playlistTable.midiFileChooser.openMidiFileAction) {
						{ setMargin(ChordHelperApplet.ZERO_INSETS); }
					});
				}
				add(Box.createRigidArea(new Dimension(5, 0)));
				add(new JButton(playlistTable.base64EncodeAction) {
					{ setMargin(ChordHelperApplet.ZERO_INSETS); }
				});
				add(Box.createRigidArea(new Dimension(5, 0)));
				add(new JButton(playlistTableModel.getMoveToTopAction()) {
					{ setMargin(ChordHelperApplet.ZERO_INSETS); }
				});
				add(Box.createRigidArea(new Dimension(5, 0)));
				add(new JButton(playlistTableModel.getMoveToBottomAction()) {
					{ setMargin(ChordHelperApplet.ZERO_INSETS); }
				});
				if( playlistTable.midiFileChooser != null ) {
					add(Box.createRigidArea(new Dimension(5, 0)));
					add(new JButton(playlistTable.midiFileChooser.saveMidiFileAction) {
						{ setMargin(ChordHelperApplet.ZERO_INSETS); }
					});
				}
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add(new JButton(playlistTable.deleteSequenceAction) {
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
			add(new JScrollPane(playlistTable));
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
