/* Minimal moonstone mock for jest: render plain DOM so tests can assert on it. */
import React from 'react';

export const Button = ({label, onClick, isDisabled, buttonRef}) => (
    <button ref={buttonRef} type="button" disabled={isDisabled} onClick={onClick}>{label}</button>
);

export const Loader = () => <div data-testid="loader"/>;

export const Typography = ({children}) => <span>{children}</span>;
