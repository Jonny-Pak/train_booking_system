import { useEffect, useState } from "react";
import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { TextStyle, Color, FontSize } from "@tiptap/extension-text-style";
import TextAlign from "@tiptap/extension-text-align";
import {
  Bold,
  Italic,
  Underline,
  Strikethrough,
  Eraser,
  List,
  ListOrdered,
  IndentIncrease,
  IndentDecrease,
  Undo2,
  Redo2,
  Maximize2,
  Minimize2,
  AlignLeft,
  AlignCenter,
  AlignRight,
} from "lucide-react";

export default function GuideEditor({ initialHtml, onChange, disabled }) {
  const [expanded, setExpanded] = useState(false);
  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [2, 3] },
        link: false,
        code: false,
        codeBlock: false,
      }),
      TextStyle,
      Color,
      FontSize,
      TextAlign.configure({ types: ["heading", "paragraph"] }),
    ],
    content: initialHtml,
    shouldRerenderOnTransaction: true,
    editorProps: {
      attributes: {
        role: "textbox",
        "aria-label": "Nội dung hướng dẫn",
        "aria-multiline": "true",
      },
    },
    onUpdate: ({ editor }) => onChange(editor.getHTML()),
  });
  useEffect(() => {
    // Locking the editor during a save does not change the document.
    editor?.setEditable(!disabled, false);
  }, [editor, disabled]);
  useEffect(() => {
    if (!expanded) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    function escape(event) {
      if (event.key === "Escape") setExpanded(false);
    }
    window.addEventListener("keydown", escape);
    return () => {
      document.body.style.overflow = previous;
      window.removeEventListener("keydown", escape);
    };
  }, [expanded]);
  if (!editor) return null;
  const tools = [
    [
      "In đậm",
      Bold,
      () => editor.chain().focus().toggleBold().run(),
      editor.isActive("bold"),
    ],
    [
      "In nghiêng",
      Italic,
      () => editor.chain().focus().toggleItalic().run(),
      editor.isActive("italic"),
    ],
    [
      "Gạch chân",
      Underline,
      () => editor.chain().focus().toggleUnderline().run(),
      editor.isActive("underline"),
    ],
    [
      "Gạch ngang",
      Strikethrough,
      () => editor.chain().focus().toggleStrike().run(),
      editor.isActive("strike"),
    ],
    [
      "Xóa định dạng",
      Eraser,
      () => editor.chain().focus().unsetAllMarks().clearNodes().run(),
    ],
    [
      "Danh sách dấu chấm",
      List,
      () => editor.chain().focus().toggleBulletList().run(),
      editor.isActive("bulletList"),
    ],
    [
      "Danh sách đánh số",
      ListOrdered,
      () => editor.chain().focus().toggleOrderedList().run(),
      editor.isActive("orderedList"),
    ],
    [
      "Tăng thụt lề danh sách",
      IndentIncrease,
      () => editor.chain().focus().sinkListItem("listItem").run(),
      false,
      !editor.can().sinkListItem("listItem"),
    ],
    [
      "Giảm thụt lề danh sách",
      IndentDecrease,
      () => editor.chain().focus().liftListItem("listItem").run(),
      false,
      !editor.can().liftListItem("listItem"),
    ],
    [
      "Căn trái",
      AlignLeft,
      () => editor.chain().focus().setTextAlign("left").run(),
      editor.isActive({ textAlign: "left" }),
    ],
    [
      "Căn giữa",
      AlignCenter,
      () => editor.chain().focus().setTextAlign("center").run(),
      editor.isActive({ textAlign: "center" }),
    ],
    [
      "Căn phải",
      AlignRight,
      () => editor.chain().focus().setTextAlign("right").run(),
      editor.isActive({ textAlign: "right" }),
    ],
    [
      "Hoàn tác",
      Undo2,
      () => editor.chain().focus().undo().run(),
      false,
      !editor.can().undo(),
    ],
    [
      "Làm lại",
      Redo2,
      () => editor.chain().focus().redo().run(),
      false,
      !editor.can().redo(),
    ],
  ];
  return (
    <div
      className={"guide-editor" + (expanded ? " guide-editor-expanded" : "")}
    >
      <div
        className="editor-toolbar"
        role="toolbar"
        aria-label="Định dạng hướng dẫn"
      >
        <select
          aria-label="Kiểu đoạn"
          disabled={disabled}
          value={
            editor.isActive("heading", { level: 2 })
              ? "h2"
              : editor.isActive("heading", { level: 3 })
                ? "h3"
                : "p"
          }
          onChange={(event) => {
            if (event.target.value === "p")
              editor.chain().focus().setParagraph().run();
            else
              editor
                .chain()
                .focus()
                .setHeading({ level: Number(event.target.value.slice(1)) })
                .run();
          }}
        >
          <option value="p">Văn bản</option>
          <option value="h2">Tiêu đề 2</option>
          <option value="h3">Tiêu đề 3</option>
        </select>
        <select
          aria-label="Cỡ chữ"
          disabled={disabled}
          value={editor.getAttributes("textStyle").fontSize || ""}
          onChange={(event) => {
            if (event.target.value)
              editor.chain().focus().setFontSize(event.target.value).run();
            else editor.chain().focus().unsetFontSize().run();
          }}
        >
          <option value="">Cỡ chữ</option>
          {[14, 16, 18, 20, 24, 28, 32].map((size) => (
            <option key={size} value={size + "px"}>
              {size}
            </option>
          ))}
        </select>
        <label className="editor-color" title="Màu chữ">
          A
          <input
            type="color"
            aria-label="Màu chữ"
            disabled={disabled}
            value={editor.getAttributes("textStyle").color || "#182b47"}
            onChange={(event) =>
              editor.chain().focus().setColor(event.target.value).run()
            }
          />
        </label>
        {tools.map(([label, Icon, action, active, unavailable]) => (
          <button
            type="button"
            key={label}
            title={label}
            aria-label={label}
            aria-pressed={Boolean(active)}
            disabled={disabled || unavailable}
            onMouseDown={(event) => event.preventDefault()}
            onClick={action}
          >
            <Icon size={19} />
          </button>
        ))}
        <button
          type="button"
          title={expanded ? "Thu nhỏ" : "Toàn màn hình"}
          aria-label={expanded ? "Thu nhỏ" : "Toàn màn hình"}
          aria-pressed={expanded}
          onClick={() => setExpanded(!expanded)}
        >
          {expanded ? <Minimize2 size={19} /> : <Maximize2 size={19} />}
        </button>
      </div>
      <EditorContent editor={editor} />
      <div className="editor-help">
        Bôi đen nội dung rồi chọn định dạng · Ctrl+B / Ctrl+I / Ctrl+U · Esc để
        thoát toàn màn hình
      </div>
    </div>
  );
}
