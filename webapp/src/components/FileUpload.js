import React, { useRef, useState } from 'react';
import {
  Box,
  Button,
  Typography,
  Paper,
  List,
  ListItem,
  ListItemText,
  ListItemSecondaryAction,
  IconButton,
} from '@mui/material';
import { CloudUpload, Delete } from '@mui/icons-material';

const FileUpload = ({ files, onFilesChange }) => {
  const fileInputRef = useRef(null);
  const [isDragging, setIsDragging] = useState(false);

  const handleFileChange = (event) => {
    const newFiles = Array.from(event.target.files);
    processFiles(newFiles);
  };

  const processFiles = async (newFiles) => {
    const filePromises = newFiles.map(file => {
      return new Promise((resolve) => {
        const reader = new FileReader();
        reader.onload = () => {
          resolve({
            fileName: file.name,
            data: reader.result
          });
        };
        reader.readAsText(file);
      });
    });

    const processedFiles = await Promise.all(filePromises);
    onFilesChange([...files, ...processedFiles]);
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    
    const newFiles = Array.from(e.dataTransfer.files);
    processFiles(newFiles);
  };

  const handleDeleteFile = (index) => {
    const updatedFiles = [...files];
    updatedFiles.splice(index, 1);
    onFilesChange(updatedFiles);
  };

  const formatFileSize = (size) => {
    if (size < 1024) return size + ' bytes';
    else if (size < 1024 * 1024) return (size / 1024).toFixed(2) + ' KB';
    else return (size / (1024 * 1024)).toFixed(2) + ' MB';
  };

  return (
    <Box sx={{ mt: 2 }}>
      <Paper
        sx={{
          p: 2,
          border: '2px dashed',
          borderColor: isDragging ? 'primary.main' : 'grey.300',
          bgcolor: isDragging ? 'rgba(25, 118, 210, 0.04)' : 'background.paper',
          textAlign: 'center',
          transition: 'all 0.3s',
        }}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
      >
        <input
          type="file"
          multiple
          onChange={handleFileChange}
          style={{ display: 'none' }}
          ref={fileInputRef}
        />
        <CloudUpload color="primary" sx={{ fontSize: 48, mb: 1 }} />
        <Typography variant="h6" gutterBottom>
          Drag & Drop CSV Files Here
        </Typography>
        <Typography variant="body2" color="textSecondary" gutterBottom>
          or
        </Typography>
        <Button
          variant="outlined"
          color="primary"
          onClick={() => fileInputRef.current.click()}
        >
          Browse Files
        </Button>
      </Paper>

      {files.length > 0 && (
        <Paper sx={{ mt: 2, p: 0 }}>
          <List dense>
            {files.map((file, index) => (
              <ListItem key={index} divider={index < files.length - 1}>
                <ListItemText
                  primary={file.fileName}
                />
                <ListItemSecondaryAction>
                  <IconButton edge="end" onClick={() => handleDeleteFile(index)}>
                    <Delete />
                  </IconButton>
                </ListItemSecondaryAction>
              </ListItem>
            ))}
          </List>
        </Paper>
      )}
    </Box>
  );
};

export default FileUpload;
