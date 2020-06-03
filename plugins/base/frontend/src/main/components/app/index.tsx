import React, {useEffect, useRef, useState} from 'react';
import {WithFuzzySearchFilter} from '../search/search';
import './index.scss';

function useComponentVisible(initialIsVisible: boolean) {
    const [isComponentVisible, setIsComponentVisible] = useState(initialIsVisible);
    const ref = useRef(null);

    const handleHideDropdown = (event: KeyboardEvent) => {
        if (event.key === "Escape") {
            setIsComponentVisible(false);
        }
    };

    const handleClickOutside = (event : MouseEvent)=> {
        // @ts-ignore
        if (ref.current && !ref.current.contains(event.target)) {
            setIsComponentVisible(false);
        }
    };

    useEffect(() => {
        document.addEventListener("keydown", handleHideDropdown, false);
        document.addEventListener("click", handleClickOutside, false);
        return () => {
            document.removeEventListener("keydown", handleHideDropdown, false);
            document.removeEventListener("click", handleClickOutside, false);
        };
    });

    return { ref, isComponentVisible, setIsComponentVisible };
}

const App: React.FC = () => {
    const {
        ref,
        isComponentVisible,
        setIsComponentVisible
    } = useComponentVisible(false);

    return <div ref={ref} className="search-content">
        {isComponentVisible && (<WithFuzzySearchFilter/>)}
        {!isComponentVisible && (
            <span onClick={() => setIsComponentVisible(true)}>
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20"><path d="M19.64 18.36l-6.24-6.24a7.52 7.52 0 1 0-1.28 1.28l6.24 6.24zM7.5 13.4a5.9 5.9 0 1 1 5.9-5.9 5.91 5.91 0 0 1-5.9 5.9z"/></svg>
          </span>)}
    </div>
}

export default App