package camidion.chordhelper;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import camidion.chordhelper.anogakki.AnoGakkiPane;
import camidion.chordhelper.chorddiagram.CapoComboBoxModel;
import camidion.chordhelper.chorddiagram.ChordDiagram;
import camidion.chordhelper.chordmatrix.ChordButtonLabel;
import camidion.chordhelper.chordmatrix.ChordMatrix;
import camidion.chordhelper.chordmatrix.ChordMatrixListener;
import camidion.chordhelper.mididevice.MidiDeviceDialog;
import camidion.chordhelper.mididevice.MidiDeviceTreeModel;
import camidion.chordhelper.mididevice.MidiSequencerModel;
import camidion.chordhelper.mididevice.SequencerMeasureView;
import camidion.chordhelper.mididevice.SequencerTimeView;
import camidion.chordhelper.mididevice.VirtualMidiDevice;
import camidion.chordhelper.midieditor.Base64Dialog;
import camidion.chordhelper.midieditor.KeySignatureLabel;
import camidion.chordhelper.midieditor.MidiEventDialog;
import camidion.chordhelper.midieditor.MidiSequenceEditorDialog;
import camidion.chordhelper.midieditor.NewSequenceDialog;
import camidion.chordhelper.midieditor.PlaylistTableModel;
import camidion.chordhelper.midieditor.SequenceTickIndex;
import camidion.chordhelper.midieditor.SequenceTrackListTableModel;
import camidion.chordhelper.midieditor.TempoSelecter;
import camidion.chordhelper.midieditor.TimeSignatureSelecter;
import camidion.chordhelper.music.Chord;
import camidion.chordhelper.music.Key;
import camidion.chordhelper.music.Range;
import camidion.chordhelper.pianokeyboard.MidiKeyboardPanel;
import camidion.chordhelper.pianokeyboard.PianoKeyboardAdapter;

/**
 * MIDI Chord Helper - Circle-of-fifth oriented chord pad
 * （アプレットクラス）
 *
 *	@auther
 *		Copyright (C) 2004-2017 ＠きよし - Akiyoshi Kamide
 *		http://www.yk.rim.or.jp/~kamide/music/chordhelper/
 */
public class ChordHelperApplet extends JApplet {
	/////////////////////////////////////////////////////////////////////
	//
	// JavaScript などからの呼び出しインターフェース
	//
	/////////////////////////////////////////////////////////////////////
	/**
	 * 未保存の修正済み MIDI ファイルがあるかどうか調べます。
	 * @return 未保存の修正済み MIDI ファイルがあれば true
	 */
	public boolean isModified() {
		return playlistModel.getSequenceModelList().stream().anyMatch(m -> m.isModified());
	}
	/**
	 * 指定された小節数の曲を、乱数で自動作曲してプレイリストへ追加し、再生します。
	 * @param measureLength 小節数
	 * @return 追加先のインデックス値（０から始まる）。追加できなかったときは -1
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 * @throws IllegalStateException MIDIシーケンサデバイスが閉じている場合
	 */
	public int addRandomSongToPlaylist(int measureLength) throws InvalidMidiDataException {
		NewSequenceDialog d = midiEditor.newSequenceDialog;
		d.setRandomChordProgression(measureLength);
		int index = playlistModel.play(d.getMidiSequence(), d.getSelectedCharset());
		midiEditor.playlistTable.getSelectionModel().setSelectionInterval(index, index);
		return index;
	}
	/**
	 * URLで指定されたMIDIファイルをプレイリストへ追加します。
	 *
	 * <p>URL の最後の / より後ろの部分がファイル名として取り込まれます。
	 * 指定できる MIDI ファイルには、param タグの midi_file パラメータと同様の制限があります。
	 * </p>
	 * @param midiFileUrl 追加するMIDIファイルのURL
	 * @return 追加先のインデックス値（０から始まる）。追加できなかったときは -1
	 */
	public int addToPlaylist(String midiFileUrl) {
		try {
			URL url = (new URI(midiFileUrl)).toURL();
			String filename = url.getFile().replaceFirst("^.*/","");
			Sequence sequence = MidiSystem.getSequence(url);
			int index = playlistModel.add(sequence, filename);
			midiEditor.playlistTable.getSelectionModel().setSelectionInterval(index, index);
			return index;
		} catch( URISyntaxException|IOException|InvalidMidiDataException e ) {
			JOptionPane.showMessageDialog(null, e, VersionInfo.NAME, JOptionPane.WARNING_MESSAGE);
		} catch( Exception e ) {
			JOptionPane.showMessageDialog(null, e, VersionInfo.NAME, JOptionPane.ERROR_MESSAGE);
		}
		return -1;
	}
	/**
	 * Base64 エンコードされた MIDI ファイルをプレイリストへ追加します。
	 *
	 * @param base64EncodedText Base64エンコードされたMIDIファイル
	 * @return 追加先のインデックス値（０から始まる）。追加できなかったときは -1
	 */
	public int addToPlaylistBase64(String base64EncodedText) {
		return addToPlaylistBase64(base64EncodedText, null);
	}
	/**
	 * ファイル名を指定して、Base64エンコードされたMIDIファイルをプレイリストへ追加します。
	 *
	 * @param base64EncodedText Base64エンコードされたMIDIファイル
	 * @param filename ディレクトリ名を除いたファイル名
	 * @return 追加先のインデックス値（０から始まる）。追加できなかったときは -1
	 */
	public int addToPlaylistBase64(String base64EncodedText, String filename) {
		Base64Dialog d = midiEditor.playlistTable.base64Dialog;
		d.setBase64TextData(base64EncodedText, filename);
		return d.addToPlaylist();
	}
	/**
	 * プレイリスト上で現在選択されているMIDIシーケンスをシーケンサへロードして再生します。
	 */
	public void play() { midiEditor.play(); }
	/**
	 * 指定されたインデックス値が示すプレイリスト上のMIDIシーケンスをシーケンサへロードして再生します。
	 * @param index インデックス値（０から始まる）
	 */
	public void play(int index) { midiEditor.play(index); }
	/**
	 * シーケンサが実行中かどうかを返します。
	 * {@link Sequencer#isRunning()} の戻り値をそのまま返します。
	 *
	 * @return 実行中のときtrue
	 */
	public boolean isRunning() { return sequencerModel.getSequencer().isRunning(); }
	/**
	 * シーケンサが再生中かどうかを返します。
	 * @return 再生中のときtrue
	 */
	public boolean isPlaying() { return isRunning(); }
	/**
	 * 現在シーケンサにロードされているMIDIデータをBase64テキストに変換した結果を返します。
	 * @return MIDIデータをBase64テキストに変換した結果
	 * @throws IOException MIDIデータの読み込みに失敗した場合
	 */
	public String getMidiDataBase64() throws IOException {
		Base64Dialog d = midiEditor.playlistTable.base64Dialog;
		d.setSequenceModel(sequencerModel.getSequenceTrackListTableModel());
		return d.getBase64TextData();
	}
	/**
	 * 現在シーケンサにロードされているMIDIファイルのファイル名を返します。
	 * @return MIDIファイル名（設定されていないときは空文字列、シーケンサにロードされていない場合null）
	 */
	public String getMidiFilename() {
		SequenceTrackListTableModel s = sequencerModel.getSequenceTrackListTableModel();
		if( s == null ) return null;
		String fn = s.getFilename();
		return fn == null ? "" : fn;
	}
	/**
	 * オクターブ位置を設定します。
	 * @param octavePosition オクターブ位置（デフォルト：4）
	 */
	public void setOctavePosition(int octavePosition) {
		keyboardPanel.keyboardCenterPanel.keyboard.octaveRangeModel.setValue(octavePosition);
	}
	/**
	 * 操作対象のMIDIチャンネルを変更します。
	 * @param ch チャンネル番号 - 1（チャンネル1のとき0、デフォルトは0）
	 */
	public void setChannel(int ch) {
		keyboardPanel.keyboardCenterPanel.keyboard.midiChComboboxModel.setSelectedChannel(ch);
	}
	/**
	 * 操作対象のMIDIチャンネルを返します。
	 * @return 操作対象のMIDIチャンネル
	 */
	public int getChannel() {
		return keyboardPanel.keyboardCenterPanel.keyboard.midiChComboboxModel.getSelectedChannel();
	}
	/**
	 * 操作対象のMIDIチャンネルに対してプログラム（音色）を設定します。
	 * @param program 音色（0～127：General MIDI に基づく）
	 */
	public void programChange(int program) {
		keyboardPanel.keyboardCenterPanel.keyboard.getSelectedChannel().programChange(program);
	}
	/**
	 * 操作対象のMIDIチャンネルに対してプログラム（音色）を設定します。
	 * 内部的には {@link #programChange(int)} を呼び出しているだけです。
	 * @param program 音色（0～127：General MIDI に基づく）
	 */
	public void setProgram(int program) { programChange(program); }
	/**
	 * 自動転回モードを変更します。初期値は true です。
	 * @param isAuto true:自動転回を行う false:自動転回を行わない
	 */
	public void setAutoInversion(boolean isAuto) {
		inversionOmissionButton.setAutoInversion(isAuto);
	}
	/**
	 * 省略したい構成音を指定します。
	 * @param index
	 * <ul>
	 * <li>-1：省略しない（デフォルト）</li>
	 * <li>0：ルート音を省略</li>
	 * <li>1：三度を省略</li>
	 * <li>2：五度を省略</li>
	 * </ul>
	 */
	public void setOmissionNoteIndex(int index) {
		inversionOmissionButton.setOmissionNoteIndex(index);
	}
	/**
	 * コードダイアグラムの表示・非表示を切り替えます。
	 * @param isVisible 表示するときtrue
	 */
	public void setChordDiagramVisible(boolean isVisible) {
		keyboardSplitPane.resetToPreferredSizes();
		if( ! isVisible ) keyboardSplitPane.setDividerLocation((double)1.0);
	}
	/**
	 * コードダイヤグラムをギターモードに変更します。
	 * 初期状態ではウクレレモードになっています。
	 */
	public void setChordDiagramForGuitar() {
		chordDiagram.setTargetInstrument(ChordDiagram.Instrument.Guitar);
	}
	/**
	 * ダークモード（暗い表示）と明るい表示とを切り替えます。
	 * @param isDark ダークモードのときtrue、明るい表示のときfalse（デフォルト）
	 */
	public void setDarkMode(boolean isDark) {
		if( darkModeToggleButton.isSelected() != isDark )
			darkModeToggleButton.doClick();
	}
	/**
	 * バージョン情報
	 */
	public static class VersionInfo {
		public static final String NAME = "MIDI Chord Helper";
		public static final String VERSION = "Ver.20180414.1";
		public static final String COPYRIGHT = "Copyright (C) 2004-2018";
		public static final String AUTHER = "＠きよし - Akiyoshi Kamide";
		public static final String URL = "http://www.yk.rim.or.jp/~kamide/music/chordhelper/";
	}
	@Override
	public String getAppletInfo() {
		return String.join(" ",
				VersionInfo.NAME, VersionInfo.VERSION,
				VersionInfo.COPYRIGHT ,VersionInfo.AUTHER, VersionInfo.URL);
	}
	/** ボタンの余白を詰めたいときに setMargin() の引数に指定するインセット */
	public static final Insets ZERO_INSETS = new Insets(0,0,0,0);

	// GUIコンポーネント（内部保存用）
	private PlaylistTableModel playlistModel;
	private MidiSequencerModel sequencerModel;
	private ChordMatrix chordMatrix;
	private JPanel keyboardSequencerPanel;
	private JPanel chordGuide;
	private JSplitPane mainSplitPane;
	private JSplitPane keyboardSplitPane;
	private ChordButtonLabel enterButtonLabel;
	private ChordTextField	lyricDisplay;
	private MidiKeyboardPanel keyboardPanel;
	private InversionAndOmissionLabel inversionOmissionButton;
	private JToggleButton darkModeToggleButton;
	private ChordDiagram chordDiagram;
	private KeySignatureLabel keysigLabel;
	private AnoGakkiPane anoGakkiPane;
	private JToggleButton anoGakkiToggleButton;
	private MidiDeviceTreeModel deviceTreeModel;

	private MidiSequenceEditorDialog midiEditor;
	/**
	 * MIDIエディタダイアログを返します。
	 * @return MIDIエディタダイアログ
	 */
	public MidiSequenceEditorDialog getMidiEditor() {
		return midiEditor;
	}

	// アイコン画像
	private Image iconImage;
	public Image getIconImage() { return iconImage; }
	private ImageIcon imageIcon;
	public ImageIcon getImageIcon() { return imageIcon; }

	@Override
	public void init() {
		// アイコン画像のロード
		URL imageIconUrl = getClass().getResource("midichordhelper.png");
		if( imageIconUrl != null ) {
			iconImage = (imageIcon = new ImageIcon(imageIconUrl)).getImage();
		}
		AboutMessagePane about = new AboutMessagePane(imageIcon);
		//
		// 背景色の取得
		Color rootPaneDefaultBgcolor = getContentPane().getBackground();
		//
		// コードダイアグラム、コードボタン、ピアノ鍵盤、およびそれらの仮想MIDIデバイスを生成
		CapoComboBoxModel capoComboBoxModel = new CapoComboBoxModel();
		chordDiagram = new ChordDiagram(capoComboBoxModel);
		chordMatrix = new ChordMatrix(capoComboBoxModel) {
			private void clearChord() {
				chordOn();
				keyboardPanel.keyboardCenterPanel.keyboard.chordDisplay.clear();
				chordDiagram.clear();
			}
			{
				addChordMatrixListener(new ChordMatrixListener(){
					@Override
					public void keySignatureChanged() {
						keyboardPanel.setCapoKey(getKeySignatureCapo());
					}
					@Override
					public void chordChanged() { chordOn(); }
				});
				capoSelecter.checkbox.addItemListener(e->clearChord());
				capoSelecter.valueSelecter.addActionListener(e->clearChord());
			}
		};
		keysigLabel = new KeySignatureLabel() {{
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					chordMatrix.setKeySignature(getKey());
				}
			});
		}};
		keyboardPanel = new MidiKeyboardPanel(chordMatrix) {{
			keyboardCenterPanel.keyboard.addPianoKeyboardListener(new PianoKeyboardAdapter() {
				@Override
				public void pianoKeyPressed(int n, InputEvent e) { chordDiagram.clear(); }
			});
			keySelecter.getKeysigCombobox().addActionListener(e->chordMatrix.setKeySignature(
				keySelecter.getSelectedKey().transposedKey(-chordMatrix.capoSelecter.getCapo())
			));
			keyboardCenterPanel.keyboard.setPreferredSize(new Dimension(571, 80));
		}};
		// MIDIデバイスツリーの構築
		VirtualMidiDevice guiMidiDevice = keyboardPanel.keyboardCenterPanel.keyboard.midiDevice;
		deviceTreeModel = new MidiDeviceTreeModel(guiMidiDevice);
		//
		// MIDIデバイスツリーを操作するダイアログの構築
		MidiDeviceDialog midiDeviceDialog = new MidiDeviceDialog(deviceTreeModel);
		midiDeviceDialog.setIconImage(iconImage);
		//
		// MIDIイベントダイアログの構築
		MidiEventDialog eventDialog = new MidiEventDialog();
		keyboardPanel.setEventDialog(eventDialog);
		//
		// MIDIエディタダイアログの構築・MIDIファイルのドロップ受付開始
		sequencerModel = deviceTreeModel.getSequencerModel();
		playlistModel = new PlaylistTableModel(sequencerModel);
		midiEditor = new MidiSequenceEditorDialog(playlistModel, eventDialog, guiMidiDevice, midiDeviceDialog.getOpenAction());
		midiEditor.setIconImage(iconImage);
		setTransferHandler(midiEditor.transferHandler);
		//
		// 歌詞表示／コード入力フィールド
		(lyricDisplay = new ChordTextField(sequencerModel)).addActionListener(
			e->chordMatrix.setSelectedChord(e.getActionCommand().trim().split("[ \t\r\n]")[0])
		);
		Border lyricDisplayDefaultBorder = lyricDisplay.getBorder();
		Color lyricDisplayDefaultBgcolor = lyricDisplay.getBackground();
		//
		// メタイベント（テンポ・拍子・調号）を受信して表示するリスナーを登録
		TempoSelecter tempoSelecter = new TempoSelecter() {{ setEditable(false); }};
		TimeSignatureSelecter timesigSelecter = new TimeSignatureSelecter() {{ setEditable(false); }};
		sequencerModel.getSequencer().addMetaEventListener(msg->{
			switch(msg.getType()) {
			case 0x51: // Tempo (3 bytes) - テンポ
				SwingUtilities.invokeLater(()->tempoSelecter.setTempo(msg.getData()));
				break;
			case 0x58: // Time signature (4 bytes) - 拍子
				SwingUtilities.invokeLater(()->timesigSelecter.setValue(msg.getData()));
				break;
			case 0x59: // Key signature (2 bytes) : 調号
				SwingUtilities.invokeLater(()->setKeySignature(new Key(msg.getData())));
				break;
			}
		});
		// 再生時間位置の移動、シーケンス名の変更、またはシーケンスの入れ替えが発生したときに呼び出されるリスナーを登録
		SongTitleLabel songTitleLabel = new SongTitleLabel();
		sequencerModel.addChangeListener(event->{
			MidiSequencerModel sequencerModel = (MidiSequencerModel) event.getSource();
			Sequencer sequencer = sequencerModel.getSequencer();
			chordMatrix.setPlaying(sequencer.isRunning());
			SequenceTrackListTableModel sequenceModel = sequencerModel.getSequenceTrackListTableModel();
			if( sequenceModel == null ) {
				songTitleLabel.clear();
				timesigSelecter.clear();
				tempoSelecter.clear();
				keysigLabel.clear();
				return;
			}
			int songIndex = playlistModel.getSequenceModelList().indexOf(sequenceModel);
			songTitleLabel.setSongTitle(songIndex, sequenceModel);
			SequenceTickIndex tickIndex = sequenceModel.getSequenceTickIndex();
			long tickPosition = sequencer.getTickPosition();
			tickIndex.tickToMeasure(tickPosition);
			chordMatrix.setBeat(tickIndex);
			if( sequencerModel.getValueIsAdjusting() || ! (sequencer.isRunning() || sequencer.isRecording()) ) {
				timesigSelecter.setValueAt(tickIndex, tickPosition);
				tempoSelecter.setTempoAt(tickIndex, tickPosition);
				setKeySignatureAt(tickIndex, tickPosition);
			}
		});
		sequencerModel.fireStateChanged();
		chordGuide = new JPanel() {{
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			add( Box.createHorizontalStrut(2) );
			add( chordMatrix.chordGuide );
			add( Box.createHorizontalStrut(2) );
			add( lyricDisplay );
			add( Box.createHorizontalStrut(2) );
			add( enterButtonLabel = new ChordButtonLabel("Enter",chordMatrix) {{
				addMouseListener(new MouseAdapter() {
					public void mousePressed(MouseEvent event) {
						boolean rightClicked = (event.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0;
						String selectedChord = rightClicked ? lyricDisplay.getText() : null;
						chordMatrix.setSelectedChord(selectedChord);
					}
				});
			}});
			add( Box.createHorizontalStrut(5) );
			add( chordMatrix.chordDisplay );
			add( Box.createHorizontalStrut(5) );
			add( darkModeToggleButton = new JToggleButton(new ButtonIcon(ButtonIcon.DARK_MODE_ICON)) {{
				setMargin(ZERO_INSETS);
				addItemListener(event->{
					boolean isDark = ((JToggleButton)event.getSource()).isSelected();
					Color col = isDark ? Color.black : null;
					getContentPane().setBackground(isDark ? Color.black : rootPaneDefaultBgcolor);
					mainSplitPane.setBackground(col);
					keyboardSplitPane.setBackground(col);
					enterButtonLabel.setDarkMode(isDark);
					chordGuide.setBackground(col);
					lyricDisplay.setBorder(isDark ? null : lyricDisplayDefaultBorder);
					lyricDisplay.setBackground(isDark ?
						chordMatrix.darkModeColorset.backgrounds[2] :
						lyricDisplayDefaultBgcolor
					);
					lyricDisplay.setForeground(isDark ? Color.white : null);
					inversionOmissionButton.setBackground(col);
					anoGakkiToggleButton.setBackground(col);
					keyboardSequencerPanel.setBackground(col);
					chordDiagram.setBackground(col);
					chordDiagram.titleLabel.setDarkMode(isDark);
					chordMatrix.setDarkMode(isDark);
					keyboardPanel.setDarkMode(isDark);
				});
				setToolTipText("Light / Dark - 明かりを点灯／消灯");
				setBorder(null);
			}});
			add( Box.createHorizontalStrut(5) );
			add( anoGakkiToggleButton = new JToggleButton(new ButtonIcon(ButtonIcon.ANO_GAKKI_ICON)) {{
				setOpaque(false);
				setMargin(ZERO_INSETS);
				setBorder(null);
				setToolTipText("あの楽器");
				addItemListener(event->
					keyboardPanel.keyboardCenterPanel.keyboard.anoGakkiPane
					= ((JToggleButton)event.getSource()).isSelected() ? anoGakkiPane : null
				);
			}} );
			add( Box.createHorizontalStrut(5) );
			add( inversionOmissionButton = new InversionAndOmissionLabel() );
			add( Box.createHorizontalStrut(5) );
			add( chordMatrix.capoSelecter );
			add( Box.createHorizontalStrut(2) );
		}};
		keyboardSequencerPanel = new JPanel() {{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			add(chordGuide);
			add(Box.createVerticalStrut(5));
			add(keyboardSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, keyboardPanel, chordDiagram) {{
				setOneTouchExpandable(true);
				setResizeWeight(1.0);
				setAlignmentX((float)0.5);
			}});
			add(Box.createVerticalStrut(5));
			add(new JPanel() {{
				setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
				add(new JPanel() {{
					setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
					add(Box.createHorizontalStrut(12));
					add(keysigLabel);
					add(Box.createHorizontalStrut(12));
					add(timesigSelecter);
					add(Box.createHorizontalStrut(12));
					add(tempoSelecter);
					add(Box.createHorizontalStrut(12));
					add(new SequencerMeasureView(sequencerModel));
					add(Box.createHorizontalStrut(12));
					add(songTitleLabel);
					add(Box.createHorizontalStrut(12));
					add(new JButton(midiEditor.openAction) {{ setMargin(ZERO_INSETS); }});
				}});
				add(new JPanel() {{
					setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
					add(Box.createHorizontalStrut(10));
					add(new JSlider(sequencerModel));
					add(new SequencerTimeView(sequencerModel));
					add(Box.createHorizontalStrut(5));
					add(new JButton(playlistModel.getMoveToTopAction()) {{ setMargin(ZERO_INSETS); }});
					add(new JButton(sequencerModel.getMoveBackwardAction()) {{ setMargin(ZERO_INSETS); }});
					add(new JToggleButton(sequencerModel.getStartStopAction()));
					add(new JButton(sequencerModel.getMoveForwardAction()) {{ setMargin(ZERO_INSETS); }});
					add(new JButton(playlistModel.getMoveToBottomAction()) {{ setMargin(ZERO_INSETS); }});
					add(new JToggleButton(playlistModel.getToggleRepeatAction()) {{ setMargin(ZERO_INSETS); }});
					add( Box.createHorizontalStrut(10) );
				}});
				add(new JPanel() {{
					add(new JButton(midiDeviceDialog.getOpenAction()));
					add(new JButton(about.getOpenAction()));
				}});
			}});
		}};
		setContentPane(new JLayeredPane() {
			{
				add(anoGakkiPane = new AnoGakkiPane(), JLayeredPane.PALETTE_LAYER);
				addComponentListener(new ComponentAdapter() {
					@Override
					public void componentResized(ComponentEvent e) { adjustSize(); }
					@Override
					public void componentShown(ComponentEvent e) { adjustSize(); }
					private void adjustSize() { anoGakkiPane.setBounds(getBounds()); }
				});
				setLayout(new BorderLayout());
				setOpaque(true);
				add(mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chordMatrix, keyboardSequencerPanel){
					{
						setResizeWeight(0.5);
						setAlignmentX((float)0.5);
						setDividerSize(5);
					}
				});
			}
		});
		setPreferredSize(new Dimension(750,470));
	}
	@Override
	public void destroy() { deviceTreeModel.forEach(m -> m.close()); }
	@Override
	public void start() {
		//
		// コードボタンで設定されている現在の調をピアノキーボードに伝える
		chordMatrix.fireKeySignatureChanged();
		//
		// アプレットのパラメータにMIDIファイルのURLが指定されていたらそれを再生する
		String midiUrl = getParameter("midi_file");
		if( midiUrl != null ) try {
			play(addToPlaylist(midiUrl));
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, e, VersionInfo.NAME, JOptionPane.WARNING_MESSAGE);
		}
	}
	@Override
	public void stop() { sequencerModel.stop(); }

	private void setKeySignature(Key key) {
		keysigLabel.setKey(key);
		chordMatrix.setKeySignature(key);
	}
	private void setKeySignatureAt(SequenceTickIndex tickIndex, long tickPosition) {
		MetaMessage msg = tickIndex.lastMetaMessageAt(
			SequenceTickIndex.MetaMessageType.KEY_SIGNATURE,
			tickPosition
		);
		if(msg == null) keysigLabel.clear(); else setKeySignature(new Key(msg.getData()));
	}

	private int[] chordOnNotes = null;
	/**
	 * 和音を発音します。
	 * <p>この関数を直接呼ぶとアルペジオが効かないので、
	 * chordMatrix.setSelectedChord() を使うことを推奨。
	 * </p>
	 */
	public void chordOn() {
		Chord playChord = chordMatrix.getSelectedChord();
		if(
			chordOnNotes != null &&
			chordMatrix.getNoteIndex() < 0 &&
			(! chordMatrix.isDragged() || playChord == null)
		) {
			// コードが鳴っている状態で、新たなコードを鳴らそうとしたり、
			// もう鳴らさないという信号が来た場合は、今鳴っている音を止める。
			Arrays.stream(chordOnNotes).forEach(n->keyboardPanel.keyboardCenterPanel.keyboard.noteOff(n));
			chordOnNotes = null;
		}
		if( playChord == null ) {
			// もう鳴らさないので、歌詞表示に通知して終了
			if( lyricDisplay != null ) lyricDisplay.appendChord(null);
			return;
		}
		// あの楽器っぽい表示
		if( keyboardPanel.keyboardCenterPanel.keyboard.anoGakkiPane != null ) {
			JComponent btn = chordMatrix.getSelectedButton();
			if( btn != null ) anoGakkiPane.start(chordMatrix, btn.getBounds());
		}
		// コードボタンからのコードを、カポつき演奏キーからオリジナルキーへ変換
		Key originalKey = chordMatrix.getKeySignatureCapo();
		Chord originalChord = playChord.transposedNewChord(
			chordMatrix.capoSelecter.getCapo(),
			chordMatrix.getKeySignature()
		);
		// 変換後のコードをキーボード画面に設定
		keyboardPanel.keyboardCenterPanel.keyboard.setChord(originalChord);
		//
		// 音域を決める。これにより鳴らす音が確定する。
		Range chordRange = new Range(
			keyboardPanel.keyboardCenterPanel.keyboard.getChromaticOffset() + 10 +
			( keyboardPanel.keyboardCenterPanel.keyboard.getOctaves() / 4 ) * 12,
			inversionOmissionButton.isAutoInversionMode() ?
			keyboardPanel.keyboardCenterPanel.keyboard.getChromaticOffset() + 21 :
			keyboardPanel.keyboardCenterPanel.keyboard.getChromaticOffset() + 33,
			-2,
			inversionOmissionButton.isAutoInversionMode()
		);
		int[] notes = originalChord.toNoteArray(chordRange, originalKey);
		//
		// 前回鳴らしたコード構成音を覚えておく
		int[] prevChordOnNotes = null;
		if( chordMatrix.isDragged() || chordMatrix.getNoteIndex() >= 0 )
			prevChordOnNotes = Arrays.copyOf(chordOnNotes, chordOnNotes.length);
		//
		// 次に鳴らす構成音を決める
		chordOnNotes = new int[notes.length];
		int i = 0;
		for( int n : notes ) {
			if( inversionOmissionButton.getOmissionNoteIndex() == i ) {
				i++; continue;
			}
			chordOnNotes[i++] = n;
			//
			// その音が今鳴っているか調べる
			boolean isNoteOn = prevChordOnNotes != null && Arrays.stream(prevChordOnNotes).anyMatch(prevN -> prevN == n);
			// すでに鳴っているのに単音を鳴らそうとする場合、
			// 鳴らそうとしている音を一旦止める。
			if( isNoteOn && chordMatrix.getNoteIndex() >= 0 &&
				notes[chordMatrix.getNoteIndex()] - n == 0
			) {
				keyboardPanel.keyboardCenterPanel.keyboard.noteOff(n);
				isNoteOn = false;
			}
			// その音が鳴っていなかったら鳴らす。
			if( ! isNoteOn ) keyboardPanel.keyboardCenterPanel.keyboard.noteOn(n);
		}
		//
		// コードを表示
		keyboardPanel.keyboardCenterPanel.keyboard.setChord(originalChord);
		chordMatrix.chordDisplay.setChord(playChord);
		//
		// コードダイアグラム用にもコードを表示
		Chord diagramChord;
		int chordDiagramCapo = chordDiagram.capoSelecterView.getCapo();
		if( chordDiagramCapo == chordMatrix.capoSelecter.getCapo() )
			diagramChord = playChord;
		else
			diagramChord = originalChord.transposedNewChord(-chordDiagramCapo, originalKey);
		chordDiagram.setChord(diagramChord);
		if( chordDiagram.recordTextButton.isSelected() )
			lyricDisplay.appendChord(diagramChord);
	}

}

