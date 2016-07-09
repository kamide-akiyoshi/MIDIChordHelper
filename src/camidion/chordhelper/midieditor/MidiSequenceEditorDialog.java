package camidion.chordhelper.midieditor;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.AccessControlException;
import java.util.Arrays;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

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
	 * このダイアログを表示するアクション
	 */
	public Action openAction = new AbstractAction("Edit/Playlist/Speed", new ButtonIcon(ButtonIcon.EDIT_ICON)) {
		{
			String tooltip = "MIDIシーケンスの編集／プレイリスト／再生速度調整";
			putValue(Action.SHORT_DESCRIPTION, tooltip);
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			if( isVisible() ) toFront(); else setVisible(true);
		}
	};

	/**
	 * エラーメッセージダイアログを表示します。
	 * @param message エラーメッセージ
	 */
	public void showError(String message) { showMessage(message, JOptionPane.ERROR_MESSAGE); }
	/**
	 * 警告メッセージダイアログを表示します。
	 * @param message 警告メッセージ
	 */
	public void showWarning(String message) { showMessage(message, JOptionPane.WARNING_MESSAGE); }
	private void showMessage(String message, int messageType) {
		JOptionPane.showMessageDialog(this, message, ChordHelperApplet.VersionInfo.NAME, messageType);
	}
	/**
	 * 確認ダイアログを表示します。
	 * @param message 確認メッセージ
	 * @return 確認OKのときtrue
	 */
	public boolean confirm(String message) {
		return JOptionPane.showConfirmDialog(this, message, ChordHelperApplet.VersionInfo.NAME,
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION ;
	}

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
				loadAndPlay((List<File>)support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				showError(e.getMessage());
				return false;
			}
		}
	};

	/**
	 * 複数のMIDIファイルを読み込み、再生されていなかったら再生します。
	 * すでに再生されていた場合、このエディタダイアログを表示します。
	 *
	 * @param fileList 読み込むMIDIファイルのリスト
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 * @see #loadAndPlay(File)
	 */
	public void loadAndPlay(List<File> fileList) {
		int indexOfAddedTop = -1;
		PlaylistTableModel playlist = sequenceListTable.getModel();
		try {
			indexOfAddedTop = playlist.addSequences(fileList);
		} catch(IOException|InvalidMidiDataException e) {
			showWarning(e.getMessage());
		} catch(AccessControlException e) {
			e.printStackTrace();
			showError(e.getMessage());
		}
		MidiSequencerModel sequencerModel = playlist.getSequencerModel();
		if( sequencerModel.getSequencer().isRunning() ) {
			String command = (String)openAction.getValue(Action.NAME);
			openAction.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command));
			return;
		}
		if( indexOfAddedTop >= 0 ) {
			try {
				playlist.loadToSequencer(indexOfAddedTop);
			} catch (InvalidMidiDataException ex) {
				ex.printStackTrace();
				showError(ex.getMessage());
				return;
			}
			sequencerModel.start();
		}
	}
	/**
	 * 1件のMIDIファイルを読み込み、再生されていなかったら再生します。
	 * すでに再生されていた場合、このエディタダイアログを表示します。
	 *
	 * @param file 読み込むMIDIファイル
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 * @see #loadAndPlay(List) loadAndPlay(List&lt;File&gt;)
	 */
	public void loadAndPlay(File file) throws InvalidMidiDataException {
		loadAndPlay(Arrays.asList(file));
	}

	private static final Insets ZERO_INSETS = new Insets(0,0,0,0);
	private static final Icon deleteIcon = new ButtonIcon(ButtonIcon.X_ICON);
	/**
	 * 新しいMIDIシーケンスを生成するダイアログ
	 */
	public NewSequenceDialog newSequenceDialog;
	/**
	 * BASE64テキスト入力ダイアログ
	 */
	public Base64Dialog base64Dialog = new Base64Dialog(this);
	/**
	 * プレイリストビュー（シーケンスリスト）
	 */
	public SequenceListTable sequenceListTable;
	/**
	 * MIDIトラックリストテーブルビュー（選択中のシーケンスの中身）
	 */
	private TrackListTable trackListTable;
	/**
	 * MIDIイベントリストテーブルビュー（選択中のトラックの中身）
	 */
	private EventListTable eventListTable;
	/**
	 * MIDIイベント入力ダイアログ（イベント入力とイベント送出で共用）
	 */
	public MidiEventDialog eventDialog = new MidiEventDialog();
	private VirtualMidiDevice outputMidiDevice;
	/**
	 * プレイリストビュー（シーケンスリスト）
	 */
	public class SequenceListTable extends JTable {
		/**
		 * ファイル選択ダイアログ（アプレットの場合は使用不可なのでnull）
		 */
		private MidiFileChooser midiFileChooser;
		/**
		 * BASE64エンコードアクション（ライブラリが見えている場合のみ有効）
		 */
		private Action base64EncodeAction;
		/**
		 * プレイリストビューを構築します。
		 * @param model プレイリストデータモデル
		 */
		public SequenceListTable(PlaylistTableModel model) {
			super(model, null, model.sequenceListSelectionModel);
			try {
				midiFileChooser = new MidiFileChooser();
			}
			catch( ExceptionInInitializerError|NoClassDefFoundError|AccessControlException e ) {
				// アプレットの場合、Webクライアントマシンのローカルファイルには
				// アクセスできないので、ファイル選択ダイアログは使用不可。
				midiFileChooser = null;
			}
			// 再生ボタンを埋め込む
			new PlayButtonCellEditor();
			new PositionCellEditor();
			//
			// 文字コード選択をプルダウンにする
			int column = PlaylistTableModel.Column.CHARSET.ordinal();
			TableCellEditor ce = new DefaultCellEditor(new JComboBox<Charset>() {{
				Set<Map.Entry<String,Charset>> entrySet = Charset.availableCharsets().entrySet();
				for( Map.Entry<String,Charset> entry : entrySet ) addItem(entry.getValue());
			}});
			getColumnModel().getColumn(column).setCellEditor(ce);
			setAutoCreateColumnsFromModel(false);
			//
			// Base64エンコードアクションの生成
			if( base64Dialog.isBase64Available() ) {
				base64EncodeAction = new AbstractAction("Base64") {
					{
						String tooltip = "Base64 text conversion - Base64テキスト変換";
						putValue(Action.SHORT_DESCRIPTION, tooltip);
					}
					@Override
					public void actionPerformed(ActionEvent e) {
						SequenceTrackListTableModel mstm = getModel().getSelectedSequenceModel();
						byte[] data = null;
						String filename = null;
						if( mstm != null ) {
							data = mstm.getMIDIdata();
							filename = mstm.getFilename();
						}
						base64Dialog.setMIDIData(data, filename);
						base64Dialog.setVisible(true);
					}
				};
			}
			TableColumnModel colModel = getColumnModel();
			for( PlaylistTableModel.Column c : PlaylistTableModel.Column.values() ) {
				TableColumn tc = colModel.getColumn(c.ordinal());
				tc.setPreferredWidth(c.preferredWidth);
				if( c == PlaylistTableModel.Column.LENGTH ) lengthColumn = tc;
			}
		}
		private TableColumn lengthColumn;
		@Override
		public void tableChanged(TableModelEvent event) {
			super.tableChanged(event);
			//
			// タイトルに合計シーケンス長を表示
			if( lengthColumn != null ) {
				int sec = getModel().getTotalSeconds();
				String title = PlaylistTableModel.Column.LENGTH.title;
				title = String.format(title+" [%02d:%02d]", sec/60, sec%60);
				lengthColumn.setHeaderValue(title);
			}
			//
			// シーケンス削除時など、合計シーケンス長が変わっても
			// 列モデルからではヘッダタイトルが再描画されないことがある。
			// そこで、ヘッダビューから repaint() で突っついて再描画させる。
			JTableHeader th = getTableHeader();
			if( th != null ) th.repaint();
		}
		/**
		 * 時間位置表示セルエディタ（ダブルクリック専用）
		 */
		private class PositionCellEditor extends AbstractCellEditor implements TableCellEditor {
			public PositionCellEditor() {
				int column = PlaylistTableModel.Column.POSITION.ordinal();
				TableColumn tc = getColumnModel().getColumn(column);
				tc.setCellEditor(this);
			}
			/**
			 * セルをダブルクリックしたときだけ編集モードに入るようにします。
			 * @param e イベント（マウスイベント）
			 * @return 編集可能になったらtrue
			 */
			@Override
			public boolean isCellEditable(EventObject e) {
				// マウスイベント以外のイベントでは編集不可
				if( ! (e instanceof MouseEvent) ) return false;
				return ((MouseEvent)e).getClickCount() == 2;
			}
			@Override
			public Object getCellEditorValue() { return null; }
			/**
			 * 編集モード時のコンポーネントを返すタイミングで
			 * そのシーケンスをシーケンサーにロードしたあと、
			 * すぐに編集モードを解除します。
			 * @return 常にnull
			 */
			@Override
			public Component getTableCellEditorComponent(
				JTable table, Object value, boolean isSelected, int row, int column
			) {
				try {
					getModel().loadToSequencer(row);
				} catch (InvalidMidiDataException ex) {
					ex.printStackTrace();
					showError(ex.getMessage());
				}
				fireEditingStopped();
				return null;
			}
		}
		/**
		 * プレイボタンを埋め込んだセルエディタ
		 */
		private class PlayButtonCellEditor extends AbstractCellEditor
			implements TableCellEditor, TableCellRenderer
		{
			private JToggleButton playButton = new JToggleButton(
				getModel().getSequencerModel().startStopAction
			) {
				{ setMargin(ZERO_INSETS); }
			};
			public PlayButtonCellEditor() {
				int column = PlaylistTableModel.Column.PLAY.ordinal();
				TableColumn tc = getColumnModel().getColumn(column);
				tc.setCellRenderer(this);
				tc.setCellEditor(this);
			}
			/**
			 * {@inheritDoc}
			 *
			 * <p>この実装では、クリックしたセルのシーケンスが
			 * シーケンサーにロードされている場合に
			 * trueを返してプレイボタンを押せるようにします。
			 * そうでない場合はプレイボタンのないセルなので、
			 * ダブルクリックされたときだけtrueを返します。
			 * </p>
			 */
			@Override
			public boolean isCellEditable(EventObject e) {
				// マウスイベント以外はデフォルトメソッドにお任せ
				if( ! (e instanceof MouseEvent) ) return super.isCellEditable(e);
				fireEditingStopped();
				MouseEvent me = (MouseEvent)e;
				//
				// クリックされたセルの行を特定
				int row = rowAtPoint(me.getPoint());
				if( row < 0 ) return false;
				PlaylistTableModel model = getModel();
				if( row >= model.getRowCount() ) return false;
				//
				// セル内にプレイボタンがあれば、シングルクリックを受け付ける。
				// プレイボタンのないセルは、ダブルクリックのみ受け付ける。
				return model.getSequenceList().get(row).isOnSequencer() || me.getClickCount() == 2;
			}
			@Override
			public Object getCellEditorValue() { return null; }
			/**
			 * {@inheritDoc}
			 *
			 * <p>この実装では、行の表すシーケンスが
			 * シーケンサーにロードされている場合にプレイボタンを返します。
			 * そうでない場合は、
			 * そのシーケンスをシーケンサーにロードしてnullを返します。
			 * </p>
			 */
			@Override
			public Component getTableCellEditorComponent(
				JTable table, Object value, boolean isSelected, int row, int column
			) {
				fireEditingStopped();
				PlaylistTableModel model = getModel();
				if( model.getSequenceList().get(row).isOnSequencer() ) return playButton;
				try {
					model.loadToSequencer(row);
				} catch (InvalidMidiDataException ex) {
					ex.printStackTrace();
					showError(ex.getMessage());
				}
				return null;
			}
			@Override
			public Component getTableCellRendererComponent(
				JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column
			) {
				PlaylistTableModel model = getModel();
				if(model.getSequenceList().get(row).isOnSequencer()) return playButton;
				Class<?> cc = model.getColumnClass(column);
				TableCellRenderer defaultRenderer = table.getDefaultRenderer(cc);
				return defaultRenderer.getTableCellRendererComponent(
					table, value, isSelected, hasFocus, row, column
				);
			}
		}
		/**
		 * このプレイリスト（シーケンスリスト）が表示するデータを提供する
		 * プレイリストモデルを返します。
		 * @return プレイリストモデル
		 */
		@Override
		public PlaylistTableModel getModel() { return (PlaylistTableModel)super.getModel(); }
		/**
		 * シーケンスを削除するアクション
		 */
		Action deleteSequenceAction = getModel().new SelectedSequenceAction(
			"Delete", MidiSequenceEditorDialog.deleteIcon,
			"Delete selected MIDI sequence - 選択した曲をプレイリストから削除"
		) {
			@Override
			public void actionPerformed(ActionEvent event) {
				PlaylistTableModel model = getModel();
				if( midiFileChooser != null ) {
					if( model.getSelectedSequenceModel().isModified() ) {
						String message =
							"Selected MIDI sequence not saved - delete it ?\n" +
							"選択したMIDIシーケンスはまだ保存されていません。削除しますか？";
						if( ! confirm(message) ) return;
					}
				}
				try {
					model.removeSelectedSequence();
				} catch (InvalidMidiDataException ex) {
					ex.printStackTrace();
					showError(ex.getMessage());
				}
			}
		};
		/**
		 * ファイル選択ダイアログ（アプレットでは使用不可）
		 */
		private class MidiFileChooser extends JFileChooser {
			{
				setFileFilter(new FileNameExtensionFilter("MIDI sequence (*.mid)", "mid"));
			}
			/**
			 * ファイル保存アクション
			 */
			public Action saveMidiFileAction = getModel().new SelectedSequenceAction(
				"Save",
				"Save selected MIDI sequence to file - 選択したMIDIシーケンスをファイルに保存"
			) {
				@Override
				public void actionPerformed(ActionEvent event) {
					PlaylistTableModel playlistModel = getModel();
					SequenceTrackListTableModel sequenceModel = playlistModel.getSelectedSequenceModel();
					String fn = sequenceModel.getFilename();
					if( fn != null && ! fn.isEmpty() ) setSelectedFile(new File(fn));
					if( showSaveDialog((Component)event.getSource()) != JFileChooser.APPROVE_OPTION ) return;
					File f = getSelectedFile();
					if( f.exists() ) {
						fn = f.getName();
						if( ! confirm("Overwrite " + fn + " ?\n" + fn + " を上書きしてよろしいですか？") ) return;
					}
					try ( FileOutputStream out = new FileOutputStream(f) ) {
						out.write(sequenceModel.getMIDIdata());
						sequenceModel.setModified(false);
						playlistModel.fireSequenceModified(sequenceModel, false);
					}
					catch( IOException ex ) {
						ex.printStackTrace();
						showError( ex.getMessage() );
					}
				}
			};
			/**
			 * ファイルを開くアクション
			 */
			public Action openMidiFileAction = new AbstractAction("Open") {
				{ putValue(Action.SHORT_DESCRIPTION, "Open MIDI file - MIDIファイルを開く"); }
				@Override
				public void actionPerformed(ActionEvent event) {
					if( showOpenDialog((Component)event.getSource()) != JFileChooser.APPROVE_OPTION ) return;
					try {
						loadAndPlay(getSelectedFile());
					} catch (InvalidMidiDataException ex) {
						ex.printStackTrace();
						showError(ex.getMessage());
					}
				}
			};
		};
	}

	/**
	 * シーケンス（トラックリスト）テーブルビュー
	 */
	public class TrackListTable extends JTable {
		/**
		 * トラックリストテーブルビューを構築します。
		 * @param model シーケンス（トラックリスト）データモデル
		 */
		public TrackListTable(SequenceTrackListTableModel model) {
			super(model, null, model.getSelectionModel());
			//
			// 録音対象のMIDIチャンネルをコンボボックスで選択できるようにする
			int colIndex = SequenceTrackListTableModel.Column.RECORD_CHANNEL.ordinal();
			TableColumn tc = getColumnModel().getColumn(colIndex);
			tc.setCellEditor(new DefaultCellEditor(new JComboBox<String>(){{
				addItem("OFF");
				for(int i=1; i <= MIDISpec.MAX_CHANNELS; i++) addItem(String.format("%d", i));
				addItem("ALL");
			}}));
			setAutoCreateColumnsFromModel(false);
			//
			titleLabel = new TitleLabel();
			model.getParent().sequenceListSelectionModel.addListSelectionListener(titleLabel);
			TableColumnModel colModel = getColumnModel();
			for( SequenceTrackListTableModel.Column c : SequenceTrackListTableModel.Column.values() )
				colModel.getColumn(c.ordinal()).setPreferredWidth(c.preferredWidth);
		}
		/**
		 * このテーブルビューが表示するデータを提供する
		 * シーケンス（トラックリスト）データモデルを返します。
		 * @return シーケンス（トラックリスト）データモデル
		 */
		@Override
		public SequenceTrackListTableModel getModel() {
			return (SequenceTrackListTableModel) super.getModel();
		}
		/**
		 * タイトルラベル
		 */
		TitleLabel titleLabel;
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
				int index = oldModel.getParent().sequenceListSelectionModel.getMinSelectionIndex();
				String text = TITLE;
				if( index >= 0 ) text = String.format(text+" - MIDI file No.%d", index);
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
				ListSelectionModel tlsm = getModel().getSelectionModel();
				deleteTrackAction.setEnabled(! tlsm.isSelectionEmpty());
				eventListTable.titleLabel.update(tlsm, getModel());
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
			{
				String tooltip = "Delete selected track - 選択したトラックを削除";
				putValue(Action.SHORT_DESCRIPTION, tooltip);
				setEnabled(false);
			}
			@Override
			public void actionPerformed(ActionEvent e) {
				String message = "Do you want to delete selected track ?\n選択したトラックを削除しますか？";
				if( confirm(message) ) getModel().deleteSelectedTracks();
			}
		};
	}

	/**
	 * MIDIイベントリストテーブルビュー（選択中のトラックの中身）
	 */
	public class EventListTable extends JTable {
		/**
		 * 新しいイベントリストテーブルを構築します。
		 * <p>データモデルとして一つのトラックのイベントリストを指定できます。
		 * トラックを切り替えたいときは {@link #setModel(TableModel)}
		 * でデータモデルを異なるトラックのものに切り替えます。
		 * </p>
		 *
		 * @param model トラック（イベントリスト）データモデル
		 */
		public EventListTable(TrackEventListTableModel model) {
			super(model, null, model.getSelectionModel());
			//
			// 列モデルにセルエディタを設定
			eventCellEditor = new MidiEventCellEditor();
			setAutoCreateColumnsFromModel(false);
			//
			eventSelectionListener = new EventSelectionListener();
			titleLabel = new TitleLabel();
			//
			TableColumnModel colModel = getColumnModel();
			for( TrackEventListTableModel.Column c : TrackEventListTableModel.Column.values() )
				colModel.getColumn(c.ordinal()).setPreferredWidth(c.preferredWidth);
		}
		/**
		 * このテーブルビューが表示するデータを提供する
		 * トラック（イベントリスト）データモデルを返します。
		 * @return トラック（イベントリスト）データモデル
		 */
		@Override
		public TrackEventListTableModel getModel() {
			return (TrackEventListTableModel) super.getModel();
		}
		/**
		 * タイトルラベル
		 */
		TitleLabel titleLabel;
		/**
		 * 親テーブルの選択トラックの変更に反応する
		 * トラック番号つきタイトルラベル
		 */
		private class TitleLabel extends JLabel {
			private static final String TITLE = "MIDI Events";
			public TitleLabel() { super(TITLE); }
			public void update(ListSelectionModel tlsm, SequenceTrackListTableModel sequenceModel) {
				String text = TITLE;
				TrackEventListTableModel oldTrackModel = getModel();
				int index = tlsm.getMinSelectionIndex();
				if( index >= 0 ) {
					text = String.format(TITLE+" - track No.%d", index);
				}
				setText(text);
				TrackEventListTableModel newTrackModel = sequenceModel.getSelectedTrackModel();
				if( oldTrackModel == newTrackModel )
					return;
				if( newTrackModel == null ) {
					newTrackModel = getModel().getParent().getParent().emptyEventListTableModel;
					queryJumpEventAction.setEnabled(false);
					queryAddEventAction.setEnabled(false);

					queryPasteEventAction.setEnabled(false);
					copyEventAction.setEnabled(false);
					deleteEventAction.setEnabled(false);
					cutEventAction.setEnabled(false);
				}
				else {
					queryJumpEventAction.setEnabled(true);
					queryAddEventAction.setEnabled(true);
				}
				oldTrackModel.getSelectionModel().removeListSelectionListener(eventSelectionListener);
				setModel(newTrackModel);
				setSelectionModel(newTrackModel.getSelectionModel());
				newTrackModel.getSelectionModel().addListSelectionListener(eventSelectionListener);
			}
		}

		/**
		 * イベント選択リスナー
		 */
		private EventSelectionListener eventSelectionListener;
		/**
		 * 選択イベントの変更に反応するリスナー
		 */
		private class EventSelectionListener implements ListSelectionListener {
			public EventSelectionListener() {
				getModel().getSelectionModel().addListSelectionListener(this);
			}
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if( e.getValueIsAdjusting() )
					return;
				if( getSelectionModel().isSelectionEmpty() ) {
					queryPasteEventAction.setEnabled(false);
					copyEventAction.setEnabled(false);
					deleteEventAction.setEnabled(false);
					cutEventAction.setEnabled(false);
				}
				else {
					copyEventAction.setEnabled(true);
					deleteEventAction.setEnabled(true);
					cutEventAction.setEnabled(true);
					TrackEventListTableModel trackModel = getModel();
					int minIndex = getSelectionModel().getMinSelectionIndex();
					MidiEvent midiEvent = trackModel.getMidiEvent(minIndex);
					if( midiEvent != null ) {
						MidiMessage msg = midiEvent.getMessage();
						if( msg instanceof ShortMessage ) {
							ShortMessage sm = (ShortMessage)msg;
							int cmd = sm.getCommand();
							if( cmd == 0x80 || cmd == 0x90 || cmd == 0xA0 ) {
								// ノート番号を持つ場合、音を鳴らす。
								MidiChannel outMidiChannels[] = outputMidiDevice.getChannels();
								int ch = sm.getChannel();
								int note = sm.getData1();
								int vel = sm.getData2();
								outMidiChannels[ch].noteOn(note, vel);
								outMidiChannels[ch].noteOff(note, vel);
							}
						}
					}
					if( pairNoteOnOffModel.isSelected() ) {
						int maxIndex = getSelectionModel().getMaxSelectionIndex();
						int partnerIndex;
						for( int i=minIndex; i<=maxIndex; i++ ) {
							if( ! getSelectionModel().isSelectedIndex(i) ) continue;
							partnerIndex = trackModel.getIndexOfPartnerFor(i);
							if( partnerIndex >= 0 && ! getSelectionModel().isSelectedIndex(partnerIndex) )
								getSelectionModel().addSelectionInterval(partnerIndex, partnerIndex);
						}
					}
				}
			}
		}
		/**
		 * Pair noteON/OFF トグルボタンモデル
		 */
		private JToggleButton.ToggleButtonModel
			pairNoteOnOffModel = new JToggleButton.ToggleButtonModel() {
				{
					addItemListener(new ItemListener() {
						public void itemStateChanged(ItemEvent e) {
							eventDialog.midiMessageForm.durationForm.setEnabled(isSelected());
						}
					});
					setSelected(true);
				}
			};
		private class EventEditContext {
			/**
			 * 編集対象トラック
			 */
			private TrackEventListTableModel trackModel;
			/**
			 * tick位置入力モデル
			 */
			private TickPositionModel tickPositionModel = new TickPositionModel();
			/**
			 * 選択されたイベント
			 */
			private MidiEvent selectedMidiEvent = null;
			/**
			 * 選択されたイベントの場所
			 */
			private int selectedIndex = -1;
			/**
			 * 選択されたイベントのtick位置
			 */
			private long currentTick = 0;
			/**
			 * 上書きして削除対象にする変更前イベント（null可）
			 */
			private MidiEvent[] midiEventsToBeOverwritten;
			/**
			 * 選択したイベントを入力ダイアログなどに反映します。
			 * @param model 対象データモデル
			 */
			private void setSelectedEvent(TrackEventListTableModel trackModel) {
				this.trackModel = trackModel;
				SequenceTrackListTableModel sequenceTableModel = trackModel.getParent();
				int ppq = sequenceTableModel.getSequence().getResolution();
				eventDialog.midiMessageForm.durationForm.setPPQ(ppq);
				tickPositionModel.setSequenceIndex(sequenceTableModel.getSequenceTickIndex());

				selectedIndex = trackModel.getSelectionModel().getMinSelectionIndex();
				selectedMidiEvent = selectedIndex < 0 ? null : trackModel.getMidiEvent(selectedIndex);
				currentTick = selectedMidiEvent == null ? 0 : selectedMidiEvent.getTick();
				tickPositionModel.setTickPosition(currentTick);
			}
			public void setupForEdit(TrackEventListTableModel trackModel) {
				MidiEvent partnerEvent = null;
				eventDialog.midiMessageForm.setMessage(
					selectedMidiEvent.getMessage(),
					trackModel.getParent().charset
				);
				if( eventDialog.midiMessageForm.isNote() ) {
					int partnerIndex = trackModel.getIndexOfPartnerFor(selectedIndex);
					if( partnerIndex < 0 ) {
						eventDialog.midiMessageForm.durationForm.setDuration(0);
					}
					else {
						partnerEvent = trackModel.getMidiEvent(partnerIndex);
						long partnerTick = partnerEvent.getTick();
						long duration = currentTick > partnerTick ?
							currentTick - partnerTick : partnerTick - currentTick ;
						eventDialog.midiMessageForm.durationForm.setDuration((int)duration);
					}
				}
				if(partnerEvent == null)
					midiEventsToBeOverwritten = new MidiEvent[] {selectedMidiEvent};
				else
					midiEventsToBeOverwritten = new MidiEvent[] {selectedMidiEvent, partnerEvent};
			}
			private Action jumpEventAction = new AbstractAction() {
				{ putValue(NAME,"Jump"); }
				public void actionPerformed(ActionEvent e) {
					long tick = tickPositionModel.getTickPosition();
					scrollToEventAt(tick);
					eventDialog.setVisible(false);
					trackModel = null;
				}
			};
			private Action pasteEventAction = new AbstractAction() {
				{ putValue(NAME,"Paste"); }
				public void actionPerformed(ActionEvent e) {
					long tick = tickPositionModel.getTickPosition();
					clipBoard.paste(trackModel, tick);
					scrollToEventAt(tick);
					// ペーストで曲の長さが変わったことをプレイリストに通知
					SequenceTrackListTableModel seqModel = trackModel.getParent();
					seqModel.getParent().fireSequenceModified(seqModel, true);
					eventDialog.setVisible(false);
					trackModel = null;
				}
			};
			private boolean applyEvent() {
				long tick = tickPositionModel.getTickPosition();
				MidiMessageForm form = eventDialog.midiMessageForm;
				SequenceTrackListTableModel seqModel = trackModel.getParent();
				MidiEvent newMidiEvent = new MidiEvent(form.getMessage(seqModel.charset), tick);
				if( midiEventsToBeOverwritten != null ) {
					// 上書き消去するための選択済イベントがあった場合
					trackModel.removeMidiEvents(midiEventsToBeOverwritten);
				}
				if( ! trackModel.addMidiEvent(newMidiEvent) ) {
					System.out.println("addMidiEvent failure");
					return false;
				}
				if(pairNoteOnOffModel.isSelected() && form.isNote()) {
					ShortMessage sm = form.createPartnerMessage();
					if(sm == null)
						scrollToEventAt( tick );
					else {
						int duration = form.durationForm.getDuration();
						if( form.isNote(false) ) {
							duration = -duration;
						}
						long partnerTick = tick + (long)duration;
						if( partnerTick < 0L ) partnerTick = 0L;
						MidiEvent partner = new MidiEvent((MidiMessage)sm, partnerTick);
						if( ! trackModel.addMidiEvent(partner) ) {
							System.out.println("addMidiEvent failure (note on/off partner message)");
						}
						scrollToEventAt(partnerTick > tick ? partnerTick : tick);
					}
				}
				seqModel.getParent().fireSequenceModified(seqModel, true);
				eventDialog.setVisible(false);
				return true;
			}
		}
		private EventEditContext editContext = new EventEditContext();
		/**
		 * 指定のTick位置へジャンプするアクション
		 */
		Action queryJumpEventAction = new AbstractAction() {
			{
				putValue(NAME,"Jump to ...");
				setEnabled(false);
			}
			public void actionPerformed(ActionEvent e) {
				editContext.setSelectedEvent(getModel());
				eventDialog.openTickForm("Jump selection to", editContext.jumpEventAction);
			}
		};
		/**
		 * 新しいイベントの追加を行うアクション
		 */
		Action queryAddEventAction = new AbstractAction() {
			{
				putValue(NAME,"New");
				setEnabled(false);
			}
			public void actionPerformed(ActionEvent e) {
				TrackEventListTableModel model = getModel();
				editContext.setSelectedEvent(model);
				editContext.midiEventsToBeOverwritten = null;
				eventDialog.openEventForm(
					"New MIDI event",
					eventCellEditor.applyEventAction,
					model.getChannel()
				);
			}
		};
		/**
		 * MIDIイベントのコピー＆ペーストを行うためのクリップボード
		 */
		private class LocalClipBoard {
			private MidiEvent copiedEventsToPaste[];
			private int copiedEventsPPQ = 0;
			public void copy(TrackEventListTableModel model, boolean withRemove) {
				copiedEventsToPaste = model.getSelectedMidiEvents();
				copiedEventsPPQ = model.getParent().getSequence().getResolution();
				if( withRemove ) model.removeMidiEvents(copiedEventsToPaste);
				boolean en = (copiedEventsToPaste != null && copiedEventsToPaste.length > 0);
				queryPasteEventAction.setEnabled(en);
			}
			public void cut(TrackEventListTableModel model) {copy(model,true);}
			public void copy(TrackEventListTableModel model){copy(model,false);}
			public void paste(TrackEventListTableModel model, long tick) {
				model.addMidiEvents(copiedEventsToPaste, tick, copiedEventsPPQ);
			}
		}
		private LocalClipBoard clipBoard = new LocalClipBoard();
		/**
		 * 指定のTick位置へ貼り付けるアクション
		 */
		Action queryPasteEventAction = new AbstractAction() {
			{
				putValue(NAME,"Paste to ...");
				setEnabled(false);
			}
			public void actionPerformed(ActionEvent e) {
				editContext.setSelectedEvent(getModel());
				eventDialog.openTickForm("Paste to", editContext.pasteEventAction);
			}
		};
		/**
		 * イベントカットアクション
		 */
		public Action cutEventAction = new AbstractAction("Cut") {
			{
				setEnabled(false);
			}
			@Override
			public void actionPerformed(ActionEvent e) {
				TrackEventListTableModel model = getModel();
				if( ! confirm("Do you want to cut selected event ?\n選択したMIDIイベントを切り取りますか？"))
					return;
				clipBoard.cut(model);
			}
		};
		/**
		 * イベントコピーアクション
		 */
		public Action copyEventAction = new AbstractAction("Copy") {
			{
				setEnabled(false);
			}
			@Override
			public void actionPerformed(ActionEvent e) {
				clipBoard.copy(getModel());
			}
		};
		/**
		 * イベント削除アクション
		 */
		public Action deleteEventAction = new AbstractAction("Delete", deleteIcon) {
			{
				setEnabled(false);
			}
			@Override
			public void actionPerformed(ActionEvent e) {
				TrackEventListTableModel model = getModel();
				if( ! confirm("Do you want to delete selected event ?\n選択したMIDIイベントを削除しますか？"))
					return;
				model.removeSelectedMidiEvents();
			}
		};
		/**
		 * MIDIイベント表のセルエディタ
		 */
		private MidiEventCellEditor eventCellEditor;
		/**
		 * MIDIイベント表のセルエディタ
		 */
		class MidiEventCellEditor extends AbstractCellEditor implements TableCellEditor {
			/**
			 * MIDIイベントセルエディタを構築します。
			 */
			public MidiEventCellEditor() {
				eventDialog.midiMessageForm.setOutputMidiChannels(outputMidiDevice.getChannels());
				eventDialog.tickPositionInputForm.setModel(editContext.tickPositionModel);
				int index = TrackEventListTableModel.Column.MESSAGE.ordinal();
				getColumnModel().getColumn(index).setCellEditor(this);
			}
			/**
			 * セルをダブルクリックしないと編集できないようにします。
			 * @param e イベント（マウスイベント）
			 * @return 編集可能になったらtrue
			 */
			@Override
			public boolean isCellEditable(EventObject e) {
				if( ! (e instanceof MouseEvent) ) return super.isCellEditable(e);
				return ((MouseEvent)e).getClickCount() == 2;
			}
			@Override
			public Object getCellEditorValue() { return null; }
			/**
			 * MIDIメッセージダイアログが閉じたときにセル編集を中止するリスナー
			 */
			private ComponentListener dialogComponentListener = new ComponentAdapter() {
				@Override
				public void componentHidden(ComponentEvent e) {
					fireEditingCanceled();
					// 用が済んだら当リスナーを除去
					eventDialog.removeComponentListener(this);
				}
			};
			/**
			 * 既存イベントを編集するアクション
			 */
			private Action editEventAction = new AbstractAction() {
				public void actionPerformed(ActionEvent e) {
					TrackEventListTableModel model = getModel();
					editContext.setSelectedEvent(model);
					if( editContext.selectedMidiEvent == null )
						return;
					editContext.setupForEdit(model);
					eventDialog.addComponentListener(dialogComponentListener);
					eventDialog.openEventForm("Change MIDI event", applyEventAction);
				}
			};
			/**
			 * イベント編集ボタン
			 */
			private JButton editEventButton = new JButton(editEventAction){{
				setHorizontalAlignment(JButton.LEFT);
			}};
			@Override
			public Component getTableCellEditorComponent(
				JTable table, Object value, boolean isSelected, int row, int column
			) {
				editEventButton.setText(value.toString());
				return editEventButton;
			}
			/**
			 * 入力したイベントを反映するアクション
			 */
			private Action applyEventAction = new AbstractAction() {
				{
					putValue(NAME,"OK");
				}
				public void actionPerformed(ActionEvent e) {
					if( editContext.applyEvent() ) fireEditingStopped();
				}
			};
		}
		/**
		 * スクロール可能なMIDIイベントテーブルビュー
		 */
		private JScrollPane scrollPane = new JScrollPane(this);
		/**
		 * 指定の MIDI tick のイベントへスクロールします。
		 * @param tick MIDI tick
		 */
		public void scrollToEventAt(long tick) {
			int index = getModel().tickToIndex(tick);
			scrollPane.getVerticalScrollBar().setValue(index * getRowHeight());
			getSelectionModel().setSelectionInterval(index, index);
		}
	}

	/**
	 * 新しい {@link MidiSequenceEditorDialog} を構築します。
	 * @param playlistTableModel このエディタが参照するプレイリストモデル
	 * @param outputMidiDevice イベントテーブルの操作音出力先MIDIデバイス
	 */
	public MidiSequenceEditorDialog(PlaylistTableModel playlistTableModel, VirtualMidiDevice outputMidiDevice) {
		this.outputMidiDevice = outputMidiDevice;
		sequenceListTable = new SequenceListTable(playlistTableModel);
		trackListTable = new TrackListTable(
			new SequenceTrackListTableModel(playlistTableModel, null, null)
		);
		eventListTable = new EventListTable(new TrackEventListTableModel(trackListTable.getModel(), null));
		newSequenceDialog = new NewSequenceDialog(playlistTableModel, outputMidiDevice);
		setTitle("MIDI Editor/Playlist - MIDI Chord Helper");
		setBounds( 150, 200, 900, 500 );
		setLayout(new FlowLayout());
		setTransferHandler(transferHandler);
		//
		// パネルレイアウト
		JPanel playlistPanel = new JPanel() {{
			JPanel playlistOperationPanel = new JPanel() {{
				setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
				add(Box.createRigidArea(new Dimension(10, 0)));
				add(new JButton(newSequenceDialog.openAction) {{ setMargin(ZERO_INSETS); }});
				if( sequenceListTable.midiFileChooser != null ) {
					add( Box.createRigidArea(new Dimension(5, 0)) );
					add(new JButton(sequenceListTable.midiFileChooser.openMidiFileAction) {
						{ setMargin(ZERO_INSETS); }
					});
				}
				if(sequenceListTable.base64EncodeAction != null) {
					add(Box.createRigidArea(new Dimension(5, 0)));
					add(new JButton(sequenceListTable.base64EncodeAction) {{ setMargin(ZERO_INSETS); }});
				}
				add(Box.createRigidArea(new Dimension(5, 0)));
				PlaylistTableModel playlistTableModel = sequenceListTable.getModel();
				add(new JButton(playlistTableModel.moveToTopAction) {{ setMargin(ZERO_INSETS); }});
				add(Box.createRigidArea(new Dimension(5, 0)));
				add(new JButton(playlistTableModel.moveToBottomAction) {{ setMargin(ZERO_INSETS); }});
				if( sequenceListTable.midiFileChooser != null ) {
					add(Box.createRigidArea(new Dimension(5, 0)));
					add(new JButton(sequenceListTable.midiFileChooser.saveMidiFileAction) {
						{ setMargin(ZERO_INSETS); }
					});
				}
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add(new JButton(sequenceListTable.deleteSequenceAction) {{ setMargin(ZERO_INSETS); }});
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add(new SequencerSpeedSlider(playlistTableModel.getSequencerModel().speedSliderModel));
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add(new JPanel() {{
					setBorder(new EtchedBorder());
					MidiSequencerModel sequencerModel = sequenceListTable.getModel().getSequencerModel();
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
				add(new JButton(trackListTable.addTrackAction) {{ setMargin(ZERO_INSETS); }});
				add(new JButton(trackListTable.deleteTrackAction) {{ setMargin(ZERO_INSETS); }});
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
				add(new JButton(eventListTable.queryJumpEventAction) {{ setMargin(ZERO_INSETS); }});
				add(new JButton(eventListTable.queryAddEventAction) {{ setMargin(ZERO_INSETS); }});
				add(new JButton(eventListTable.copyEventAction) {{ setMargin(ZERO_INSETS); }});
				add(new JButton(eventListTable.cutEventAction) {{ setMargin(ZERO_INSETS); }});
				add(new JButton(eventListTable.queryPasteEventAction) {{ setMargin(ZERO_INSETS); }});
				add(new JButton(eventListTable.deleteEventAction) {{ setMargin(ZERO_INSETS); }});
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
